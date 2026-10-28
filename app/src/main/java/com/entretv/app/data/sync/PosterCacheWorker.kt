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
import com.entretv.app.data.local.PosterRow
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.player.PlayerManager
import com.entretv.app.ui.CatalogOptions
import com.entretv.app.ui.CatalogOrder
import com.entretv.app.ui.ImageFailures
import com.entretv.app.ui.ImageTraffic
import com.entretv.app.ui.PosterPrefetcher
import com.entretv.app.ui.PosterThumbs
import com.entretv.app.ui.SortOrder
import com.entretv.app.ui.TitleCleaner
import com.entretv.app.ui.cardImage
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Baja en segundo plano las miniaturas de los pósters de Películas y Series de la cuenta: primero
 * el comienzo de cada categoría, después el resto, de lo más nuevo a lo más viejo. Solo con Wi‑Fi o
 * cable y con espacio. Una vez por actualización: lo que ya está en disco se salta (si se corta
 * sigue donde quedó) y se borran las miniaturas de títulos que ya no están.
 *
 * Sigue mientras se navega, de a una y cediendo la red a los pósters en pantalla; se detiene
 * mientras hay algo en el reproductor o se actualiza el catálogo.
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
        val pid = inputData.getString(KEY_PLAYLIST)?.takeIf { repository.playlist(it) != null }
            ?: repository.currentPlaylist()?.id
            ?: return Result.success()
        val refreshedAt = settings.lastRefreshAt(pid)
        if (refreshedAt == 0L || posters.catalogDone(pid, refreshedAt)) return Result.success()
        val plan = withContext(Dispatchers.IO) { plan(pid) }
        posters.prefetch(plan.missing, background = true, gate = { slot -> canContinue(slot) })
        if (isStopped || lowSpace) return Result.success()
        fillMicros(plan.ordered)
        if (isStopped || lowSpace) return Result.success()
        // Con el servidor de imágenes caído casi nada llegó: la próxima vez se intenta de nuevo.
        val left = withContext(Dispatchers.IO) { stillMissing(plan.ordered) }
        if (left <= plan.ordered.size * MAX_LEFT_PERCENT / 100) posters.markCatalogDone(pid, refreshedAt)
        return Result.success()
    }

    /**
     * Antes de cada póster: nada en el reproductor ni actualización en curso, los pósters en
     * pantalla ya llegaron y el disco no tiene miniaturas en fila. Con la app a la vista baja de a una
     * (solo [slot] 0).
     */
    private suspend fun canContinue(slot: Int): Boolean {
        val manager = player.get()
        var waitedUi = 0
        while (!isStopped) {
            combine(manager.isPlayingFlow, manager.currentKey) { playing, key -> playing || key != null }.first { !it }
            repository.syncing.first { !it }
            if (slot > 0) AppForeground.visible.first { !it }
            if (ImageTraffic.active.value > 0) {
                // Con tope: un póster colgado no deja la descarga parada para siempre.
                withTimeoutOrNull(UI_WAIT_MAX_MS) { ImageTraffic.active.first { it == 0 } }
                delay(UI_CALM_MS)
                if (ImageTraffic.active.value > 0 && waitedUi++ < UI_WAIT_ROUNDS) continue
            }
            PosterThumbs.pending.first { it <= MAX_PENDING_THUMBS }
            if (manager.currentKey.value == null && !repository.syncing.value) break
        }
        // statfs en cada póster sobra: cada tanto alcanza.
        if (checks.getAndIncrement() % SPACE_CHECK_EVERY == 0) lowSpace = !enoughSpace()
        return !lowSpace && !isStopped
    }

    private fun enoughSpace(): Boolean = applicationContext.noBackupFilesDir.usableSpace >= MIN_FREE_BYTES

    private class Plan(
        /** Pósters del catálogo visible en el orden en que se bajan. */
        val ordered: List<String>,
        /** Los de [ordered] que todavía no tienen miniatura. */
        val missing: List<String>,
        val pruned: Int
    )

    /**
     * Lista liviana (sin armar ni ordenar títulos) con las mismas categorías ocultas y bloqueadas que
     * Inicio. La carpeta de miniaturas se lee una sola vez; antes se borran las de títulos quitados.
     */
    private suspend fun plan(pid: String): Plan {
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
        val azKey = { name: String -> CatalogOrder.normalize(TitleCleaner.clean(name, o.cleanNames)) }
        val movieRows = repository.moviePosters(pid)
        val seriesRows = repository.seriesPosters(pid)
        val movies = sectionPosters(movieRows, PlaylistRepository.SECTION_MOVIES, o, order, azKey, CatalogOrder::movieGroupOf)
        val series = sectionPosters(seriesRows, PlaylistRepository.SECTION_SERIES, o, order, azKey, CatalogOrder::seriesGroupOf)
        val ordered = (interleave(movies.first, series.first) + interleave(movies.second, series.second))
            .distinctBy { cardImage(it) }

        // Con la base vacía (actualización a medias) no se borra nada.
        val pruned = if (movieRows.isEmpty() && seriesRows.isEmpty()) 0
        else {
            val keep = repository.allPosterUrls().mapTo(HashSet()) { PosterThumbs.keyOf(cardImage(it)) }
            PosterThumbs.prune(applicationContext, keep)
        }
        val stored = PosterThumbs.storedKeys(applicationContext)
        val failures = ImageFailures.get(applicationContext)
        val missing = ordered.filter { url ->
            val card = cardImage(url)
            PosterThumbs.keyOf(card) !in stored && !failures.isRemembered(card)
        }
        return Plan(ordered, missing, pruned)
    }

    /** Comienzo de cada categoría ([FIRST_PER_CATEGORY]) y el resto, de lo más nuevo a lo más viejo. */
    private fun sectionPosters(
        rows: List<PosterRow>,
        section: String,
        o: CatalogOptions,
        order: SortOrder,
        azKey: (String) -> String,
        groupOf: (String) -> String
    ): Pair<List<String>, List<String>> {
        val skip = CatalogOrder.excluder(section, o)
        val groups = LinkedHashMap<String, MutableList<String>>()
        val rest = ArrayList<String>(rows.size)
        for (row in rows) {
            val group = groupOf(row.categoryName)
            if (skip(group) || row.id in o.hidden || (o.kids && ParentalControl.looksAdult(row.name))) continue
            groups.getOrPut(group) { ArrayList() }.add(row.url)
            rest.add(row.url)
        }
        val names = CatalogOrder.orderedCategories(section, groups.keys, o, CatalogOrder.orderFor(section, order), azKey)
            .filter { it in groups }
        val head = names.flatMap { groups.getValue(it).take(FIRST_PER_CATEGORY) }
        return head to rest
    }

    /** Vistas previas de las miniaturas guardadas antes de que existieran. */
    private suspend fun fillMicros(urls: List<String>) {
        val (stored, micros) = withContext(Dispatchers.IO) {
            PosterThumbs.storedKeys(applicationContext) to PosterThumbs.storedMicroKeys(applicationContext)
        }
        for (url in urls) {
            val key = PosterThumbs.keyOf(cardImage(url))
            if (key !in stored || key in micros) continue
            if (!canContinue(0)) return
            withContext(Dispatchers.IO) { PosterThumbs.makeMicro(applicationContext, key) }
            // Con la app a la vista el disco es de los pósters en pantalla.
            if (AppForeground.isForeground) delay(MICRO_GAP_MS)
        }
    }

    private fun stillMissing(urls: List<String>): Int {
        val stored = PosterThumbs.storedKeys(applicationContext)
        val failures = ImageFailures.get(applicationContext)
        return urls.count { url ->
            val card = cardImage(url)
            PosterThumbs.keyOf(card) !in stored && !failures.isRemembered(card)
        }
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
        /** Pausa tras los pósters en pantalla: suelen venir varios seguidos al moverse. */
        private const val UI_CALM_MS = 500L
        private const val UI_WAIT_MAX_MS = 15_000L
        private const val UI_WAIT_ROUNDS = 3
        private const val MICRO_GAP_MS = 50L
        /** Si falta más que esto al terminar, la pasada no cuenta como completa. */
        private const val MAX_LEFT_PERCENT = 2

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
