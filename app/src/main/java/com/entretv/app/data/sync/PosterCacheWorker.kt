package com.entretv.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.entretv.app.data.AppForeground
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.player.PlayerManager
import com.entretv.app.ui.BrowseItem
import com.entretv.app.ui.CatalogOptions
import com.entretv.app.ui.CatalogOrder
import com.entretv.app.ui.PosterPrefetcher
import com.entretv.app.ui.PosterThumbs
import com.entretv.app.ui.SortOrder
import com.entretv.app.ui.TitleCleaner
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Baja en segundo plano las miniaturas de todos los pósters de Películas y Series de la cuenta,
 * en el orden en que se ven (primero el comienzo de cada categoría, después el resto). Solo con
 * Wi‑Fi o cable y con espacio; lo que ya está en disco se salta, así que si se corta sigue donde
 * quedó. Espera mientras la app está a la vista o se reproduce un video, para no quitarle disco,
 * CPU ni red a lo que se está usando.
 */
@HiltWorker
class PosterCacheWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PlaylistRepository,
    private val settings: SettingsRepository,
    private val parental: ParentalControl,
    private val posters: PosterPrefetcher,
    private val player: Lazy<PlayerManager>
) : CoroutineWorker(context, params) {

    private val checks = AtomicInteger()
    @Volatile private var lowSpace = false

    override suspend fun doWork(): Result {
        if (!enoughSpace()) return Result.success()
        // Con la app a la vista compite por disco y CPU con los pósters en pantalla.
        AppForeground.visible.first { !it }
        // #region agent log
        val agentT0 = android.os.SystemClock.elapsedRealtime()
        com.entretv.app.data.DebugLog.workerRunning = true
        try {
        // #endregion
        val urls = withContext(Dispatchers.Default) { catalogPosters() }
        // #region agent log
        com.entretv.app.data.DebugLog.log("PosterCacheWorker.doWork", "catalog ready", "A,F", mapOf(
            "urls" to urls.size, "computeMs" to android.os.SystemClock.elapsedRealtime() - agentT0, "attempt" to runAttemptCount
        ))
        // #endregion
        posters.prefetch(urls, gate = { canContinue() })
        // #region agent log
        } finally {
            com.entretv.app.data.DebugLog.workerRunning = false
            com.entretv.app.data.DebugLog.log("PosterCacheWorker.doWork", "worker end", "A,F", mapOf(
                "elapsedMs" to android.os.SystemClock.elapsedRealtime() - agentT0, "stopped" to isStopped
            ))
        }
        // #endregion
        return Result.success()
    }

    private suspend fun canContinue(): Boolean {
        player.get().isPlayingFlow.first { !it }
        AppForeground.visible.first { !it }
        PosterThumbs.pending.first { it <= MAX_PENDING_THUMBS }
        // statfs en cada póster sobra: cada tanto alcanza.
        if (checks.getAndIncrement() % SPACE_CHECK_EVERY == 0) lowSpace = !enoughSpace()
        return !lowSpace && !isStopped
    }

    private fun enoughSpace(): Boolean = applicationContext.noBackupFilesDir.usableSpace >= MIN_FREE_BYTES

    /** Con las mismas reglas que Inicio: categorías ocultas, bloqueadas y orden elegido. */
    private suspend fun catalogPosters(): List<String> {
        val pid = inputData.getString(KEY_PLAYLIST)?.takeIf { repository.playlist(it) != null }
            ?: repository.currentPlaylist()?.id
            ?: return emptyList()
        val catalog = settings.catalogPrefs.first()
        val o = CatalogOptions(
            playlistId = pid,
            serverOrder = listOf(PlaylistRepository.SECTION_MOVIES, PlaylistRepository.SECTION_SERIES).associateWith { section ->
                repository.categoriesSnapshot(pid, section).associate { it.name to it.position }
            },
            prefs = repository.categoryPrefsSnapshot(pid).associateBy { it.section to it.name },
            hidden = repository.hiddenKeys(),
            cleanNames = catalog.cleanNames,
            locked = parental.locked.first(),
            kids = parental.kids.value
        )
        val order = SortOrder.fromName(catalog.defaultSort)
        val clean = { raw: String -> TitleCleaner.clean(raw, o.cleanNames) }
        val titleKeys = HashMap<String, String>()
        val sort = { items: List<BrowseItem>, s: SortOrder ->
            CatalogOrder.sortItems(items, s) { title -> titleKeys.getOrPut(title) { CatalogOrder.normalize(title) } }
        }
        val azKey = { name: String -> CatalogOrder.normalize(clean(name)) }
        val adultTitles = HashMap<String, Boolean>()
        val adult = { name: String -> adultTitles.getOrPut(name) { ParentalControl.looksAdult(name) } }

        val movies = repository.movies(pid).first()
        val series = repository.series(pid).first()
        fun moviePosters(perCategory: Int) = CatalogOrder.categoryPosters(
            movies, PlaylistRepository.SECTION_MOVIES, o, order, null, perCategory,
            CatalogOrder::movieGroup, { it.id }, { adult(it.name) }, { BrowseItem.Movie(it, clean(it.name)) }, sort, azKey
        )
        fun seriesPosters(perCategory: Int) = CatalogOrder.categoryPosters(
            series, PlaylistRepository.SECTION_SERIES, o, order, null, perCategory,
            CatalogOrder::seriesGroup, { it.id }, { adult(it.name) }, { BrowseItem.Show(it, clean(it.name)) }, sort, azKey
        )
        val head = interleave(moviePosters(FIRST_PER_CATEGORY), seriesPosters(FIRST_PER_CATEGORY))
        return (head + moviePosters(Int.MAX_VALUE) + seriesPosters(Int.MAX_VALUE)).distinct()
    }

    /** Alternadas: si se corta pronto, las dos secciones ya tienen el comienzo de sus categorías. */
    private fun <T> interleave(a: List<T>, b: List<T>): List<T> = buildList(a.size + b.size) {
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let(::add)
            b.getOrNull(i)?.let(::add)
        }
    }

    companion object {
        private const val WORK_NAME = "poster-cache"
        private const val KEY_PLAYLIST = "playlist"
        private const val FIRST_PER_CATEGORY = 12
        private const val SPACE_CHECK_EVERY = 25
        private const val MAX_PENDING_THUMBS = 3
        private const val MIN_FREE_BYTES = 1024L * 1024 * 1024

        /**
         * Encarga la descarga; si ya hay una en curso la deja seguir, salvo con [replace] (el
         * catálogo cambió). Abre la base de WorkManager: fuera del hilo principal.
         */
        fun enqueue(work: WorkManager, playlistId: String? = null, replace: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<PosterCacheWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .apply { if (playlistId != null) setInputData(workDataOf(KEY_PLAYLIST to playlistId)) }
                .build()
            work.enqueueUniqueWork(WORK_NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }

        fun enqueue(context: Context, playlistId: String? = null, replace: Boolean = false) =
            enqueue(WorkManager.getInstance(context), playlistId, replace)
    }
}
