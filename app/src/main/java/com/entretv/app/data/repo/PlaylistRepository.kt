package com.entretv.app.data.repo

import com.entretv.app.data.local.ChannelDao
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.EpgDao
import com.entretv.app.data.local.EpgEntity
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.PlaylistDao
import com.entretv.app.data.local.PlaylistEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.ProgressDao
import com.entretv.app.data.local.SeriesEntity
import com.entretv.app.data.local.VodDao
import com.entretv.app.data.local.VodMovieEntity
import android.util.Base64
import android.util.LruCache
import com.entretv.app.data.local.FavoriteDao
import com.entretv.app.data.local.FavoriteEntity
import com.entretv.app.data.remote.ContentDetails
import com.entretv.app.data.remote.M3uParser
import com.entretv.app.data.local.CatalogDao
import com.entretv.app.data.local.CategoryEntity
import com.entretv.app.data.local.CategoryPrefEntity
import com.entretv.app.data.local.HiddenItemEntity
import com.entretv.app.data.remote.XtreamApi
import com.entretv.app.data.remote.XtreamCategory
import com.entretv.app.data.remote.XtreamInfoParser
import com.entretv.app.data.remote.XtreamSeries
import com.entretv.app.data.remote.XtreamStream
import com.entretv.app.data.remote.XtreamVodStream
import com.entretv.app.data.remote.asEpochSeconds
import com.entretv.app.data.remote.asRating
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val vodDao: VodDao,
    private val progressDao: ProgressDao,
    private val epgDao: EpgDao,
    private val favoriteDao: FavoriteDao,
    private val catalogDao: CatalogDao,
    private val xtreamApi: XtreamApi,
    private val json: Json
) {
    private val detailsCache = LruCache<String, ContentDetails>(200)

    /** Pocos servidores Xtream aguantan muchas descargas grandes a la vez: cortan la respuesta. */
    private val requestSlots = Semaphore(2)

    val playlists: Flow<List<PlaylistEntity>> = playlistDao.observeAll()

    val continueWatching: Flow<List<PlaybackProgressEntity>> =
        progressDao.observeContinueWatching()

    fun channels(playlistId: String): Flow<List<ChannelEntity>> =
        channelDao.observeByPlaylist(playlistId)

    fun movies(playlistId: String): Flow<List<VodMovieEntity>> =
        vodDao.observeMovies(playlistId)

    fun series(playlistId: String): Flow<List<SeriesEntity>> =
        vodDao.observeSeries(playlistId)

    fun episodes(seriesKey: String): Flow<List<EpisodeEntity>> =
        vodDao.observeEpisodes(seriesKey)

    suspend fun currentPlaylist(): PlaylistEntity? = playlistDao.getAll().firstOrNull()

    suspend fun channelById(id: String): ChannelEntity? = channelDao.getById(id)
    suspend fun movieById(id: String): VodMovieEntity? = vodDao.getMovie(id)
    suspend fun seriesById(id: String): SeriesEntity? = vodDao.getSeries(id)
    suspend fun episodeById(id: String): EpisodeEntity? = vodDao.getEpisode(id)
    suspend fun channelSnapshot(playlistId: String): List<ChannelEntity> =
        channelDao.getByPlaylist(playlistId)
    suspend fun episodeSnapshot(seriesKey: String): List<EpisodeEntity> =
        vodDao.getEpisodes(seriesKey)

    // ============================ Agregar / sync ============================

    suspend fun addM3uPlaylist(name: String, url: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = xtreamApi.fetchText(url).string()
                val id = UUID.randomUUID().toString()
                val channels = M3uParser.parse(id, body)
                require(channels.isNotEmpty()) { "La lista M3U está vacía o es inválida" }
                playlistDao.upsert(PlaylistEntity(id = id, name = name, type = "M3U", url = url))
                channelDao.upsertAll(channels)
                saveCategories(id, SECTION_LIVE, channels.map { it.groupTitle })
                id
            }
        }

    suspend fun addXtreamPlaylist(name: String, baseUrl: String, username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = serverRoot(baseUrl)
                val login = xtreamApi.login(apiUrl(root), username, password)
                require(login.userInfo?.status == "Active") { "Cuenta inactiva o credenciales inválidas" }

                val id = UUID.randomUUID().toString()
                playlistDao.upsert(
                    PlaylistEntity(id = id, name = name, type = "XTREAM", url = root, username = username, password = password)
                )
                refreshPlaylist(id)
                id
            }
        }

    /**
     * Actualización rápida al entrar: muestra lo cacheado al instante y
     * sincroniza en segundo plano. Cada sección (canales, películas, series)
     * es independiente: si una falla se conserva lo guardado y se informa.
     *
     * @return secciones que no se pudieron actualizar, con el motivo.
     */
    suspend fun refreshAll(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            playlistDao.getAll().flatMap { playlist -> refreshPlaylistInternal(playlist) }
        }
    }

    suspend fun refreshPlaylist(playlistId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val p = playlistDao.get(playlistId) ?: error("Playlist no encontrada")
            refreshPlaylistInternal(p)
        }
    }

    private suspend fun refreshPlaylistInternal(p: PlaylistEntity): List<String> = coroutineScope {
        when (p.type) {
            "M3U" -> listOfNotNull(
                runSection("Canales") {
                    val body = withRetry { xtreamApi.fetchText(p.url).string() }
                    val channels = M3uParser.parse(p.id, body)
                    if (channels.isNotEmpty()) {
                        channelDao.deleteByPlaylist(p.id)
                        channelDao.upsertAll(channels)
                        saveCategories(p.id, SECTION_LIVE, channels.map { it.groupTitle })
                    }
                }
            )
            else -> {
                val live = async { runSection("Canales") { syncLive(p) } }
                val movies = async { runSection("Películas") { syncMovies(p) } }
                val series = async { runSection("Series") { syncSeries(p) } }
                listOfNotNull(live.await(), movies.await(), series.await())
            }
        }
    }

    private suspend fun syncLive(p: PlaylistEntity) = coroutineScope {
        val catsJob = async { fetchOrNull(p, "get_live_categories", CATEGORY_LIST) }
        val streams = fetchAction(p, "get_live_streams", ListSerializer(XtreamStream.serializer()))
        val cats = catsJob.await()
        if (streams.isEmpty()) return@coroutineScope
        // Si las categorías no llegaron, cada canal conserva el grupo que ya tenía.
        val previous = if (cats == null) {
            channelDao.getByPlaylist(p.id).associate { it.streamKey to it.groupTitle }
        } else emptyMap()
        val names = cats.orEmpty().associate { it.categoryId to it.categoryName }
        val root = serverRoot(p.url)
        val channels = streams.mapIndexed { index, s ->
            val key = s.streamId.toString()
            ChannelEntity(
                id = "${p.id}:${s.streamId}",
                playlistId = p.id,
                streamKey = key,
                name = s.name,
                logoUrl = s.streamIcon.orEmpty(),
                groupTitle = names[s.categoryId] ?: previous[key] ?: s.categoryId,
                streamUrl = "${root}live/${p.username}/${p.password}/${s.streamId}.m3u8",
                epgChannelId = s.epgChannelId.orEmpty(),
                number = index + 1
            )
        }
        channelDao.deleteByPlaylist(p.id)
        channelDao.upsertAll(channels)
        saveCategories(p.id, SECTION_LIVE, categoryOrder(p.id, SECTION_LIVE, cats) + channels.map { it.groupTitle })
    }

    private suspend fun syncMovies(p: PlaylistEntity) = coroutineScope {
        val catsJob = async { fetchOrNull(p, "get_vod_categories", CATEGORY_LIST) }
        val streams = fetchAction(p, "get_vod_streams", ListSerializer(XtreamVodStream.serializer()))
        val cats = catsJob.await()
        if (streams.isEmpty()) return@coroutineScope
        val previous = if (cats == null) {
            vodDao.getMovies(p.id).associate { it.movieId to it.categoryName }
        } else emptyMap()
        val names = cats.orEmpty().associate { it.categoryId to it.categoryName }
        val root = serverRoot(p.url)
        val movies = streams.mapIndexed { index, m ->
            val key = m.streamId.toString()
            VodMovieEntity(
                id = "${p.id}:${m.streamId}",
                playlistId = p.id,
                movieId = key,
                name = m.name,
                icon = m.streamIcon.orEmpty(),
                categoryId = m.categoryId,
                categoryName = names[m.categoryId] ?: previous[key] ?: m.categoryId,
                streamUrl = "${root}movie/${p.username}/${p.password}/${m.streamId}.${m.containerExtension}",
                added = m.added.asEpochSeconds(),
                rating = m.rating.asRating(),
                position = index
            )
        }
        vodDao.deleteMoviesByPlaylist(p.id)
        vodDao.upsertMovies(movies)
        saveCategories(p.id, SECTION_MOVIES, categoryOrder(p.id, SECTION_MOVIES, cats) + movies.map { it.categoryName })
    }

    private suspend fun syncSeries(p: PlaylistEntity) = coroutineScope {
        val catsJob = async { fetchOrNull(p, "get_series_categories", CATEGORY_LIST) }
        val list = fetchAction(p, "get_series", ListSerializer(XtreamSeries.serializer()))
        val cats = catsJob.await()
        if (list.isEmpty()) return@coroutineScope
        val previous = if (cats == null) {
            vodDao.getSeriesList(p.id).associate { it.seriesId to it.categoryName }
        } else emptyMap()
        val names = cats.orEmpty().associate { it.categoryId to it.categoryName }
        val series = list.mapIndexed { index, s ->
            val key = s.seriesId.toString()
            SeriesEntity(
                id = "${p.id}:${s.seriesId}",
                playlistId = p.id,
                seriesId = key,
                name = s.name,
                cover = s.cover.orEmpty(),
                categoryName = names[s.categoryId] ?: previous[key] ?: s.categoryId,
                plot = s.plot.orEmpty(),
                added = s.lastModified.asEpochSeconds(),
                rating = s.rating.asRating(),
                position = index
            )
        }
        vodDao.deleteSeriesByPlaylist(p.id)
        vodDao.deleteEpisodesByPlaylist(p.id)
        vodDao.upsertSeries(series)
        saveCategories(p.id, SECTION_SERIES, categoryOrder(p.id, SECTION_SERIES, cats) + series.map { it.categoryName })
    }

    /** Orden de categorías del servidor; si no llegaron, el último guardado. */
    private suspend fun categoryOrder(playlistId: String, section: String, cats: List<XtreamCategory>?): List<String> =
        cats?.map { it.categoryName } ?: catalogDao.getCategories(playlistId, section).map { it.name }

    private suspend fun saveCategories(playlistId: String, section: String, names: List<String>) {
        val ordered = names.filter { it.isNotBlank() }.distinct()
        catalogDao.replaceCategories(
            playlistId, section,
            ordered.mapIndexed { index, name -> CategoryEntity(playlistId, section, name, index) }
        )
    }

    /** Descarga una acción de player_api.php y la decodifica; reintenta si llega cortada. */
    private suspend fun <T> fetchAction(p: PlaylistEntity, action: String, serializer: KSerializer<T>): T =
        withRetry {
            val text = requestSlots.withPermit {
                xtreamApi.action(apiUrl(p.url), p.username, p.password, action).string()
            }
            json.decodeFromString(serializer, text)
        }

    private suspend fun <T> fetchOrNull(p: PlaylistEntity, action: String, serializer: KSerializer<T>): T? =
        try {
            fetchAction(p, action, serializer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var wait = 1_000L
        repeat(MAX_ATTEMPTS - 1) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isRetryable()) throw e
            }
            delay(wait)
            wait *= 2
        }
        return block()
    }

    private fun Throwable.isRetryable(): Boolean =
        this is IOException || this is SerializationException || (this is HttpException && code() >= 500)

    private suspend fun runSection(label: String, block: suspend () -> Unit): String? =
        try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "$label (${e.friendlyReason()})"
        }

    private fun Throwable.friendlyReason(): String = when (this) {
        is SerializationException -> "el servidor cortó la respuesta"
        is SocketTimeoutException -> "el servidor tardó demasiado"
        is HttpException -> "error ${code()} del servidor"
        is IOException ->
            if (message.orEmpty().contains("unexpected end", ignoreCase = true)) "el servidor cortó la respuesta"
            else "sin conexión con el servidor"
        else -> message ?: "error desconocido"
    }

    // ============================ Organización ============================

    fun categories(playlistId: String): Flow<List<CategoryEntity>> = catalogDao.observeCategories(playlistId)

    fun categoryPrefs(playlistId: String): Flow<List<CategoryPrefEntity>> = catalogDao.observePrefs(playlistId)

    val hiddenItems: Flow<List<HiddenItemEntity>> = catalogDao.observeHidden()

    suspend fun saveCategoryPrefs(prefs: List<CategoryPrefEntity>) =
        withContext(Dispatchers.IO) { catalogDao.upsertPrefs(prefs) }

    suspend fun categoryPrefsSnapshot(playlistId: String): List<CategoryPrefEntity> =
        withContext(Dispatchers.IO) { catalogDao.getPrefs(playlistId) }

    suspend fun hideItem(item: HiddenItemEntity) = withContext(Dispatchers.IO) { catalogDao.hide(item) }

    suspend fun unhideItem(contentKey: String) = withContext(Dispatchers.IO) { catalogDao.unhide(contentKey) }

    suspend fun hiddenKeys(): Set<String> = withContext(Dispatchers.IO) { catalogDao.hiddenKeys().toSet() }

    /** Claves de películas y series en las categorías dadas (para filtrar "Seguir viendo"). */
    suspend fun contentKeysIn(playlistId: String, movieCategories: List<String>, seriesCategories: List<String>): Set<String> =
        withContext(Dispatchers.IO) {
            val movies = if (movieCategories.isEmpty()) emptyList() else vodDao.movieIdsIn(playlistId, movieCategories)
            val series = if (seriesCategories.isEmpty()) emptyList() else vodDao.seriesIdsIn(playlistId, seriesCategories)
            (movies + series).toSet()
        }

    /** Descarga y guarda los episodios de una serie (al abrir su ficha). */
    suspend fun syncSeriesEpisodes(series: SeriesEntity): Result<Unit> =
        seriesDetails(series).map { }

    /** Ficha de la película (sinopsis, reparto, puntuación…). Solo listas Xtream. */
    suspend fun movieDetails(movie: VodMovieEntity): Result<ContentDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                detailsCache.get(movie.id)?.let { return@runCatching it }
                val p = playlistDao.get(movie.playlistId)
                val vodId = movie.movieId.toIntOrNull()
                if (p == null || p.type != "XTREAM" || vodId == null) return@runCatching ContentDetails()
                val details = XtreamInfoParser.movie(xtreamApi.vodInfo(apiUrl(p.url), p.username, p.password, vodId))
                detailsCache.put(movie.id, details)
                details
            }
        }

    /** Ficha de la serie; de paso guarda sus episodios con sinopsis e imagen. */
    suspend fun seriesDetails(series: SeriesEntity): Result<ContentDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = playlistDao.get(series.playlistId)
                val seriesId = series.seriesId.toIntOrNull()
                if (p == null || p.type != "XTREAM" || seriesId == null) {
                    return@runCatching ContentDetails(plot = series.plot)
                }
                val root = serverRoot(p.url)
                val raw = xtreamApi.seriesInfo(apiUrl(p.url), p.username, p.password, seriesId)
                val episodes = XtreamInfoParser.episodes(raw).map { ep ->
                    EpisodeEntity(
                        id = "${series.id}#${ep.id}",
                        seriesKey = series.id,
                        episodeId = ep.id,
                        season = ep.season,
                        episodeNum = ep.episodeNum,
                        title = ep.title.ifBlank { "Episodio ${ep.episodeNum}" },
                        streamUrl = "${root}series/${p.username}/${p.password}/${ep.id}.${ep.containerExtension}",
                        plot = ep.plot,
                        imageUrl = ep.imageUrl,
                        durationSecs = ep.durationSecs
                    )
                }
                if (episodes.isNotEmpty()) {
                    vodDao.deleteEpisodesForSeries(series.id)
                    vodDao.upsertEpisodes(episodes)
                }
                val details = XtreamInfoParser.series(raw).let { d ->
                    if (d.plot.isEmpty()) d.copy(plot = series.plot) else d
                }
                detailsCache.put(series.id, details)
                details
            }
        }

    fun cachedDetails(contentKey: String): ContentDetails? = detailsCache.get(contentKey)

    // ============================ Mi lista ============================

    val favorites: Flow<List<FavoriteEntity>> = favoriteDao.observeAll()

    fun isFavorite(contentKey: String): Flow<Boolean> = favoriteDao.observeIsFavorite(contentKey)

    /** Agrega o quita de "Mi lista". Devuelve true si quedó agregado. */
    suspend fun toggleFavorite(favorite: FavoriteEntity): Boolean = withContext(Dispatchers.IO) {
        if (favoriteDao.isFavorite(favorite.contentKey)) {
            favoriteDao.delete(favorite.contentKey)
            false
        } else {
            favoriteDao.upsert(favorite)
            true
        }
    }

    fun observeProgress(contentKey: String): Flow<PlaybackProgressEntity?> = progressDao.observe(contentKey)

    fun observeSeriesProgress(seriesKey: String): Flow<List<PlaybackProgressEntity>> =
        progressDao.observeByPrefix("$seriesKey#")

    // ============================ Progreso (Netflix) ============================

    suspend fun saveProgress(entity: PlaybackProgressEntity) {
        if (entity.kind == "LIVE") return // el live no guarda minuto
        withContext(Dispatchers.IO) { progressDao.upsert(entity) }
    }

    suspend fun progressFor(contentKey: String): PlaybackProgressEntity? =
        progressDao.get(contentKey)

    suspend fun progressForMany(keys: List<String>): List<PlaybackProgressEntity> =
        if (keys.isEmpty()) emptyList() else progressDao.getMany(keys)

    /** Quitar un elemento de "Seguir viendo". */
    suspend fun removeProgress(contentKey: String) =
        withContext(Dispatchers.IO) { progressDao.delete(contentKey) }

    /** Reiniciar una serie completa (todos sus episodios vuelven a 0). */
    suspend fun resetSeries(seriesKey: String) =
        withContext(Dispatchers.IO) { progressDao.deleteSeriesProgress("$seriesKey#") }

    /** Borrar TODO el progreso (películas y series). */
    suspend fun resetAllProgress() =
        withContext(Dispatchers.IO) { progressDao.clearAll() }

    // ============================ EPG ============================

    suspend fun refreshEpg(channel: ChannelEntity) {
        withContext(Dispatchers.IO) {
            runCatching {
                val p = playlistDao.get(channel.playlistId) ?: return@runCatching
                if (p.type != "XTREAM" || channel.streamKey.toIntOrNull() == null) return@runCatching
                val response = xtreamApi.shortEpg(apiUrl(p.url), p.username, p.password, channel.streamKey.toInt())
                val now = System.currentTimeMillis() / 1000
                val programs = response.listings.mapNotNull { listing ->
                    val title = listing.titleBase64.decodeBase64() ?: return@mapNotNull null
                    EpgEntity(
                        channelKey = channel.epgChannelId.ifEmpty { channel.streamKey },
                        title = title,
                        description = listing.descriptionBase64.decodeBase64().orEmpty(),
                        start = listing.startTimestamp,
                        end = listing.stopTimestamp
                    ).takeIf { it.end > now - 6 * 3600 }
                }
                if (programs.isNotEmpty()) epgDao.upsertAll(programs)
                epgDao.purgeOld(now - 24 * 3600)
            }
        }
    }

    suspend fun upcomingEpg(channel: ChannelEntity, limit: Int = 12): List<EpgEntity> =
        epgDao.upcomingFor(
            channelKey = channel.epgChannelId.ifEmpty { channel.streamKey },
            now = System.currentTimeMillis() / 1000,
            limit = limit
        )

    private fun String.decodeBase64(): String? = runCatching {
        val cleaned = replace("\n", "").replace("\r", "")
        String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8).trim()
    }.getOrNull()

    /** Raíz del servidor, sin player_api.php, siempre con / final. */
    private fun serverRoot(raw: String): String {
        var value = raw.trim().trimEnd('/')
        if (value.endsWith("/player_api.php", ignoreCase = true)) {
            value = value.substring(0, value.length - "/player_api.php".length).trimEnd('/')
        }
        return "$value/"
    }

    private fun apiUrl(raw: String): String = serverRoot(raw) + "player_api.php"

    companion object {
        const val SECTION_LIVE = "LIVE"
        const val SECTION_MOVIES = "MOVIES"
        const val SECTION_SERIES = "SERIES"
        private const val MAX_ATTEMPTS = 4
        private val CATEGORY_LIST = ListSerializer(XtreamCategory.serializer())
    }
}
