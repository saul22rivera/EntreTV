package com.entretv.app.tv

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.entretv.app.R
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.PlaylistEntity
import com.entretv.app.data.repo.MetadataRepository
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.repo.SeriesResume
import com.entretv.app.data.repo.SeriesResumeRule
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.TitleCleaner
import com.entretv.app.ui.isTvDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Pantalla de inicio de Android TV (8.0+): "Seguir viendo" (Watch Next) con lo que el usuario
 * dejó a medias y el canal "EntreTV" con lo último agregado al catálogo. En celulares no hace nada.
 *
 * Las tarjetas abren la app con ids internos ([TvDeepLink]); las imágenes solo se publican si
 * la dirección no lleva usuario ni contraseña de la cuenta.
 */
@Singleton
class TvHomePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val metadata: MetadataRepository,
    private val settings: SettingsRepository,
    private val parental: ParentalControl
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val lock = Mutex()
    private val resolver get() = context.contentResolver

    /**
     * Sigue los cambios mientras la app está abierta. Mientras se reproduce el progreso se guarda
     * cada 5 s y la espera no se cumple: se publica al pausar, salir o quitar algo de la fila.
     */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (!isSupported(context)) return
        scope.launch {
            delay(START_DELAY_MS)
            combine(
                repository.continueWatching, settings.continueDismissed,
                repository.hiddenItems, parental.pinSet
            ) { _, _, _, _ -> }
                .debounce(WATCH_NEXT_DEBOUNCE_MS)
                .collect { publishWatchNext() }
        }
        scope.launch {
            delay(START_DELAY_MS)
            refreshChannel(force = false)
            var wasSyncing = false
            repository.syncing.collect { syncing ->
                if (wasSyncing && !syncing) refreshChannel(force = false)
                wasSyncing = syncing
            }
        }
        scope.launch {
            repository.playlists.map { it.isEmpty() }.distinctUntilChanged().filter { it }
                .collect { clearChannel() }
        }
    }

    suspend fun publishWatchNext() {
        if (!isSupported(context)) return
        lock.withLock { withContext(Dispatchers.IO) { guarded("watch next") { syncWatchNext(watchNextEntries()) } } }
    }

    /** Sin [force], como mucho una vez por día (el catálogo se actualiza una o dos veces por día). */
    suspend fun refreshChannel(force: Boolean) {
        if (!isSupported(context)) return
        lock.withLock { withContext(Dispatchers.IO) { guarded("channel") { syncChannel(force) } } }
    }

    private suspend fun clearChannel() {
        lock.withLock {
            withContext(Dispatchers.IO) {
                guarded("channel") {
                    findChannel()?.let { channel -> previewPrograms(channel.id).forEach { deletePreview(it.id) } }
                    prefs.edit().remove(KEY_CHANNEL_AT).apply()
                }
            }
        }
    }

    // ============================ Seguir viendo ============================

    private class WatchNextEntry(val internalId: String, val version: Long, val program: WatchNextProgram)

    /** Mismo criterio que la fila "Seguir viendo" de la app: una tarjeta por película o por serie. */
    private suspend fun watchNextEntries(): List<WatchNextEntry> {
        val progress = repository.continueWatching.first()
        if (progress.isEmpty()) return emptyList()
        val guard = ImageGuard(repository.playlists.first())
        val cleanNames = settings.catalogPrefs.first().cleanNames
        val dismissed = settings.continueDismissed.first()
        // La pantalla de inicio la ve cualquiera: con PIN puesto, nada de adultos aunque esté desbloqueado.
        val adult = if (parental.pinSet.first()) {
            progress.map { it.playlistId }.distinct().flatMapTo(HashSet()) { adultContentKeys(it) }
        } else emptySet()
        val excluded = repository.hiddenKeys() + adult
        val visible = progress.filter { it.contentKey.substringBefore('#') !in excluded }

        val movies = visible.filter { it.kind == "MOVIE" && !it.isCompleted }
            .mapNotNull { movieEntry(it, cleanNames, guard) }
        val series = visible.filter { it.kind == "EPISODE" }
            .groupBy { it.contentKey.substringBefore('#') }
            .mapNotNull { (seriesKey, list) ->
                val last = list.maxOf { it.updatedAt }
                if ((dismissed[seriesKey] ?: 0L) >= last) null
                else seriesEntry(seriesKey, list, last, cleanNames, guard)
            }
        return (movies + series).sortedByDescending { it.version }.take(MAX_WATCH_NEXT)
    }

    private suspend fun movieEntry(p: PlaybackProgressEntity, cleanNames: Boolean, guard: ImageGuard): WatchNextEntry? {
        val movie = repository.movieById(p.contentKey) ?: return null
        val cached = metadata.cachedDetails(movie.id)
        val program = WatchNextProgram.Builder()
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_MOVIE)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(p.updatedAt)
            .setTitle(TitleCleaner.clean(movie.name, cleanNames))
            .setLastPlaybackPositionMillis(p.positionMs.toMillisInt())
            .setDurationMillis(p.durationMs.toMillisInt())
            .setIntentUri(TvDeepLink.play("MOVIE", movie.id).toIntentUri(context))
            .setInternalProviderId(movie.id)
            .setInternalProviderFlag1(p.updatedAt)
            .apply {
                guard.pick(
                    cached?.backdropUrl to LANDSCAPE, p.imageUrl to POSTER,
                    movie.icon to POSTER, cached?.posterUrl to POSTER
                )?.let { (url, ratio) -> setPosterArtUri(Uri.parse(url)).setPosterArtAspectRatio(ratio) }
            }
            .build()
        return WatchNextEntry(movie.id, p.updatedAt, program)
    }

    /** En curso: el episodio a retomar; terminado: el siguiente ("A continuación"). */
    private suspend fun seriesEntry(
        seriesKey: String,
        progress: List<PlaybackProgressEntity>,
        last: Long,
        cleanNames: Boolean,
        guard: ImageGuard
    ): WatchNextEntry? {
        val series = repository.seriesById(seriesKey) ?: return null
        val resume = SeriesResumeRule.resolve(repository.episodeSnapshot(seriesKey), progress)
        val anchor = resume.anchor ?: return null
        val continuing = when (resume.mode) {
            SeriesResume.Mode.RESUME -> true
            SeriesResume.Mode.NEXT -> false
            else -> return null
        }
        val episode = resume.episode
        val cached = metadata.cachedDetails(seriesKey)
        val link = if (episode != null) TvDeepLink.play("EPISODE", episode.id) else TvDeepLink.details("SERIES", seriesKey)
        val program = WatchNextProgram.Builder()
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE)
            .setWatchNextType(
                if (continuing) TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                else TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
            )
            .setLastEngagementTimeUtcMillis(last)
            .setTitle(TitleCleaner.clean(series.name, cleanNames))
            .setIntentUri(link.toIntentUri(context))
            .setInternalProviderId(seriesKey)
            .setInternalProviderFlag1(last)
            .apply {
                if (episode != null) {
                    setSeasonNumber(episode.season)
                    setEpisodeNumber(episode.episodeNum)
                    if (episode.title.isNotBlank()) setEpisodeTitle(episode.title)
                }
                if (continuing) {
                    setLastPlaybackPositionMillis(anchor.positionMs.toMillisInt())
                    setDurationMillis(anchor.durationMs.toMillisInt())
                } else if ((episode?.durationSecs ?: 0) > 0) {
                    setDurationMillis((episode!!.durationSecs * 1_000L).toMillisInt())
                }
                guard.pick(
                    episode?.imageUrl to LANDSCAPE, cached?.backdropUrl to LANDSCAPE,
                    series.cover to POSTER, cached?.posterUrl to POSTER
                )?.let { (url, ratio) -> setPosterArtUri(Uri.parse(url)).setPosterArtAspectRatio(ratio) }
            }
            .build()
        return WatchNextEntry(seriesKey, last, program)
    }

    /**
     * Inserta, actualiza o borra según el id interno. Si el usuario la quitó del inicio
     * (browsable = 0) no se vuelve a poner hasta que cambie el progreso.
     */
    private fun syncWatchNext(entries: List<WatchNextEntry>) {
        val existing = query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION) {
            WatchNextProgram.fromCursor(it)
        }
        val wanted = entries.associateBy { it.internalId }
        val kept = HashMap<String, WatchNextProgram>()
        existing.forEach { old ->
            val id = old.internalProviderId
            if (id == null || id !in wanted || id in kept) deleteWatchNext(old.id) else kept[id] = old
        }
        entries.forEach { entry ->
            val old = kept[entry.internalId]
            when {
                old == null -> resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, entry.program.toContentValues())
                !old.isBrowsable && old.internalProviderFlag1 == entry.version -> Unit
                !old.isBrowsable -> {
                    deleteWatchNext(old.id)
                    resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, entry.program.toContentValues())
                }
                else -> resolver.update(
                    TvContractCompat.buildWatchNextProgramUri(old.id), entry.program.toContentValues(), null, null
                )
            }
        }
    }

    private fun deleteWatchNext(id: Long) {
        resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
    }

    // ============================ Canal EntreTV ============================

    private class ChannelItem(val key: String, val kind: String, val title: String, val image: String, val description: String)

    private suspend fun syncChannel(force: Boolean) {
        val playlist = repository.currentPlaylist() ?: return
        val existing = findChannel()
        val channelId = existing?.id ?: createChannel()
        val stale = System.currentTimeMillis() - prefs.getLong(KEY_CHANNEL_AT, 0L) >= CHANNEL_INTERVAL_MS
        if (!force && existing != null && !stale) return
        val items = channelItems(playlist)
        if (items.isEmpty()) return
        syncPreviewPrograms(channelId, items)
        prefs.edit().putLong(KEY_CHANNEL_AT, System.currentTimeMillis()).apply()
    }

    /** Lo último agregado, películas y series intercaladas, sin ocultos ni categorías de adultos. */
    private suspend fun channelItems(playlist: PlaylistEntity): List<ChannelItem> {
        val pid = playlist.id
        val guard = ImageGuard(repository.playlists.first())
        val cleanNames = settings.catalogPrefs.first().cleanNames
        val hidden = repository.hiddenKeys()
        val prefsByKey = repository.categoryPrefsSnapshot(pid).associateBy { it.section to it.name }
        fun allowed(section: String, category: String): Boolean {
            val pref = prefsByKey[section to category]
            return pref?.hidden != true && !ParentalControl.isAdult(category, pref)
        }
        val movies = repository.recentMovies(pid, CHANNEL_CANDIDATES)
            .filter { it.id !in hidden && allowed(PlaylistRepository.SECTION_MOVIES, it.categoryName) }
            .mapNotNull { m ->
                guard.safe(m.icon)?.let { ChannelItem(m.id, "MOVIE", TitleCleaner.clean(m.name, cleanNames), it, "") }
            }
        val series = repository.recentSeries(pid, CHANNEL_CANDIDATES)
            .filter { it.id !in hidden && allowed(PlaylistRepository.SECTION_SERIES, it.categoryName) }
            .mapNotNull { s ->
                guard.safe(s.cover)?.let { ChannelItem(s.id, "SERIES", TitleCleaner.clean(s.name, cleanNames), it, s.plot) }
            }
        val mixed = ArrayList<ChannelItem>(CHANNEL_SIZE)
        for (i in 0 until maxOf(movies.size, series.size)) {
            movies.getOrNull(i)?.let(mixed::add)
            series.getOrNull(i)?.let(mixed::add)
            if (mixed.size >= CHANNEL_SIZE) break
        }
        return mixed.take(CHANNEL_SIZE)
    }

    private fun syncPreviewPrograms(channelId: Long, items: List<ChannelItem>) {
        val wanted = items.mapTo(HashSet()) { it.key }
        val kept = HashMap<String, PreviewProgram>()
        previewPrograms(channelId).forEach { old ->
            val id = old.internalProviderId
            if (id == null || id !in wanted || id in kept) deletePreview(old.id) else kept[id] = old
        }
        items.forEachIndexed { index, item ->
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(
                    if (item.kind == "MOVIE") TvContractCompat.PreviewProgramColumns.TYPE_MOVIE
                    else TvContractCompat.PreviewProgramColumns.TYPE_TV_SERIES
                )
                .setTitle(item.title)
                .setPosterArtUri(Uri.parse(item.image))
                .setPosterArtAspectRatio(POSTER)
                .setIntentUri(TvDeepLink.details(item.kind, item.key).toIntentUri(context))
                .setInternalProviderId(item.key)
                .setWeight(items.size - index)
                .apply { if (item.description.isNotBlank()) setDescription(item.description.take(DESCRIPTION_MAX)) }
                .build()
            val old = kept[item.key]
            when {
                old == null -> resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
                // El usuario la sacó del canal: no se vuelve a poner.
                !old.isBrowsable -> Unit
                else -> resolver.update(TvContractCompat.buildPreviewProgramUri(old.id), program.toContentValues(), null, null)
            }
        }
    }

    private fun previewPrograms(channelId: Long): List<PreviewProgram> =
        query(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), PreviewProgram.PROJECTION) {
            PreviewProgram.fromCursor(it)
        }

    private fun deletePreview(id: Long) {
        resolver.delete(TvContractCompat.buildPreviewProgramUri(id), null, null)
    }

    private fun findChannel(): Channel? =
        query(TvContractCompat.Channels.CONTENT_URI, Channel.PROJECTION) { Channel.fromCursor(it) }
            .firstOrNull { it.internalProviderId == CHANNEL_INTERNAL_ID }

    /** El primer canal de la app se muestra sin pedirle permiso al usuario. */
    private fun createChannel(): Long {
        val launch = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, com.entretv.app.MainActivity::class.java)
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(context.getString(R.string.app_name))
            .setAppLinkIntentUri(Uri.parse(launch.toUri(Intent.URI_INTENT_SCHEME)))
            .setInternalProviderId(CHANNEL_INTERNAL_ID)
            .build()
        val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: error("channel insert")
        val id = ContentUris.parseId(uri)
        channelLogo()?.let { ChannelLogoUtils.storeChannelLogo(context, id, it) }
        runCatching { TvContractCompat.requestChannelBrowsable(context, id) }
        prefs.edit().remove(KEY_CHANNEL_AT).apply()
        return id
    }

    private fun channelLogo(): Bitmap? = runCatching {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(LOGO_PX, LOGO_PX)
    }.getOrNull()

    // ============================ Auxiliares ============================

    private suspend fun adultContentKeys(playlistId: String): Set<String> {
        val prefs = repository.categoryPrefsSnapshot(playlistId)
        val byName = prefs.associateBy { it.section to it.name }
        suspend fun adultIn(section: String): List<String> {
            val names = repository.categoriesSnapshot(playlistId, section).map { it.name } +
                prefs.filter { it.section == section && it.adult == true }.map { it.name }
            return names.distinct().filter { ParentalControl.isAdult(it, byName[section to it]) }
        }
        return repository.contentKeysIn(
            playlistId, adultIn(PlaylistRepository.SECTION_MOVIES), adultIn(PlaylistRepository.SECTION_SERIES)
        )
    }

    private fun <T> query(uri: Uri, projection: Array<String>, read: (Cursor) -> T): List<T> =
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(read(cursor)) }
        }.orEmpty()

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin TvProvider o el sistema rechazó la fila: la app sigue igual.
            Log.w(TAG, "$what: ${e.javaClass.simpleName}")
        }
    }

    private fun Long.toMillisInt(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    companion object {
        private const val TAG = "TvHome"
        private const val PREFS = "entre_tv_home"
        private const val KEY_CHANNEL_AT = "channel_published_at"
        private const val CHANNEL_INTERNAL_ID = "entretv-default"
        private const val START_DELAY_MS = 8_000L
        private const val WATCH_NEXT_DEBOUNCE_MS = 8_000L
        private const val CHANNEL_INTERVAL_MS = 20 * 60 * 60 * 1000L
        private const val MAX_WATCH_NEXT = 20
        private const val CHANNEL_SIZE = 20
        private const val CHANNEL_CANDIDATES = 120
        private const val DESCRIPTION_MAX = 300
        private const val LOGO_PX = 192
        private const val LANDSCAPE = TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9
        private const val POSTER = TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_MOVIE_POSTER

        fun isSupported(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isTvDevice(context)
    }
}

/**
 * Solo pasan imágenes http(s) cuya dirección no lleve el usuario ni la contraseña de ninguna
 * cuenta guardada (algunos paneles los ponen en la URL). Ante la duda, sin imagen.
 */
private class ImageGuard(playlists: List<PlaylistEntity>) {
    private val secrets = playlists.flatMap { listOf(it.username, it.password) }
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun safe(url: String?): String? {
        val raw = url?.trim().orEmpty()
        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        val decoded = Uri.decode(raw)
        val lower = decoded.lowercase()
        if (SENSITIVE.any { it in lower }) return null
        val tokens = decoded.split(SEPARATORS).toHashSet()
        if (secrets.any { it in tokens || (it.length >= 4 && decoded.contains(it)) }) return null
        return raw
    }

    fun pick(vararg candidates: Pair<String?, Int>): Pair<String, Int>? =
        candidates.firstNotNullOfOrNull { (url, ratio) -> safe(url)?.let { tileSized(it) to ratio } }

    /** Las tarjetas del inicio son chicas: los fondos de TMDB en 1280 px o más pesan de más. */
    private fun tileSized(url: String): String = TMDB_LARGE.replace(url, "/t/p/w780/")

    private companion object {
        val SENSITIVE = listOf("username=", "password=", "user=", "pass=", "token=", "auth=")
        val SEPARATORS = Regex("[/?&=:@#;,.]")
        val TMDB_LARGE = Regex("/t/p/(w1280|original)/")
    }
}
