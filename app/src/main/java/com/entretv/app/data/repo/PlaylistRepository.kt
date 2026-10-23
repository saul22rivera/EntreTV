package com.entretv.app.data.repo

import android.content.Context
import android.util.Base64
import android.util.Log
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.entretv.app.BuildConfig
import com.entretv.app.R
import com.entretv.app.ui.UiText
import com.entretv.app.ui.uiText
import com.entretv.app.data.backgroundExecutor
import com.entretv.app.data.local.AppDatabase
import com.entretv.app.data.local.CatalogDao
import com.entretv.app.data.local.CategoryEntity
import com.entretv.app.data.local.CategoryPrefEntity
import com.entretv.app.data.local.ChannelDao
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.DownloadDao
import com.entretv.app.data.local.EpgDao
import com.entretv.app.data.local.EpgEntity
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.FavoriteDao
import com.entretv.app.data.local.FavoriteEntity
import com.entretv.app.data.local.HiddenItemEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.PlaylistDao
import com.entretv.app.data.local.PlaylistEntity
import com.entretv.app.data.local.ProgressDao
import com.entretv.app.data.local.SeriesEntity
import com.entretv.app.data.local.VodDao
import com.entretv.app.data.local.VodMovieEntity
import com.entretv.app.data.remote.ContentDetails
import com.entretv.app.data.remote.M3uParser
import com.entretv.app.data.remote.XtreamApi
import com.entretv.app.data.remote.XtreamCategory
import com.entretv.app.data.remote.XtreamInfoParser
import com.entretv.app.data.remote.XtreamLogin
import com.entretv.app.data.remote.XtreamSeries
import com.entretv.app.data.remote.XtreamStream
import com.entretv.app.data.remote.XtreamVodStream
import com.entretv.app.data.remote.asEpochSeconds
import com.entretv.app.data.remote.asField
import com.entretv.app.data.remote.asRating
import com.entretv.app.data.remote.asYear
import com.entretv.app.data.remote.yearFromName
import com.entretv.app.data.settings.AccountInfo
import com.entretv.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import okio.Buffer
import okio.ForwardingSource
import okio.HashingSource
import okio.buffer
import retrofit2.HttpException

/** Error de inicio de sesión con un mensaje listo para mostrar en el idioma de la app. */
class AccountException(val text: UiText) : Exception() {
    constructor(@StringRes id: Int, vararg args: Any) : this(UiText.Res(id, args.toList()))
}

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val database: AppDatabase,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val vodDao: VodDao,
    private val progressDao: ProgressDao,
    private val epgDao: EpgDao,
    private val favoriteDao: FavoriteDao,
    private val catalogDao: CatalogDao,
    private val downloadDao: DownloadDao,
    private val xtreamApi: XtreamApi,
    private val settings: SettingsRepository,
    private val json: Json
) {
    private val detailsCache = LruCache<String, ContentDetails>(200)

    /** Pocos servidores Xtream aguantan muchas descargas grandes a la vez: cortan la respuesta. */
    private val requestSlots = Semaphore(2)

    /** Huella de la última respuesta de cada sección: si no cambió, no se reescribe la base. */
    private val syncPrefs = context.getSharedPreferences("entre_sync", Context.MODE_PRIVATE)

    /** Una sola actualización a la vez; las que llegan mientras tanto esperan y casi siempre se saltan. */
    private val refreshLock = Mutex()

    /** Bajar y leer el catálogo (canales, películas y series a la vez) sin quitarle CPU al video. */
    private val syncDispatcher = backgroundExecutor("catalog-sync", 3).asCoroutineDispatcher()

    private val _syncing = MutableStateFlow(false)
    /** True mientras se baja el catálogo: al entrar, a mano o en la actualización programada. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

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

    suspend fun playlist(id: String): PlaylistEntity? = playlistDao.get(id)

    suspend fun channelById(id: String): ChannelEntity? = channelDao.getById(id)
    suspend fun movieById(id: String): VodMovieEntity? = vodDao.getMovie(id)
    suspend fun seriesById(id: String): SeriesEntity? = vodDao.getSeries(id)
    suspend fun episodeById(id: String): EpisodeEntity? = vodDao.getEpisode(id)
    suspend fun channelSnapshot(playlistId: String): List<ChannelEntity> =
        channelDao.getByPlaylist(playlistId)
    suspend fun episodeSnapshot(seriesKey: String): List<EpisodeEntity> =
        vodDao.getEpisodes(seriesKey)
    /** Lo último que agregó el servidor (o su orden, si no informa fecha). */
    suspend fun recentMovies(playlistId: String, limit: Int): List<VodMovieEntity> =
        vodDao.recentMovies(playlistId, limit)
    suspend fun recentSeries(playlistId: String, limit: Int): List<SeriesEntity> =
        vodDao.recentSeries(playlistId, limit)

    // ============================ Cuenta ============================

    /**
     * Inicia sesión en el servidor fijo de la app. Si ya había una cuenta con ese usuario
     * en ese servidor se reutiliza (conserva progreso y Mi lista).
     */
    suspend fun login(name: String, username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = serverRoot(settings.serverUrl.first())
                val account = validateAccount(root, username, password)
                val existing = playlistDao.getAll().firstOrNull {
                    it.type == "XTREAM" && serverRoot(it.url) == root && it.username == username
                }
                val id = existing?.id ?: UUID.randomUUID().toString()
                playlistDao.upsert(
                    PlaylistEntity(
                        id = id, name = name.ifBlank { username }, type = "XTREAM", url = root,
                        username = username, password = password, addedAt = existing?.addedAt ?: System.currentTimeMillis()
                    )
                )
                settings.setLastRefreshAt(id, 0L)
                settings.setAccountInfo(id, account)
                id
            }
        }

    /** Cambia nombre, usuario o contraseña; el servidor no se toca. Valida antes de guardar. */
    suspend fun updateAccount(id: String, name: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = playlistDao.get(id) ?: throw AccountException(R.string.account_missing)
                val credentialsChanged = current.username != username || current.password != password
                val account = if (credentialsChanged && current.type == "XTREAM") {
                    validateAccount(serverRoot(current.url), username, password)
                } else null
                playlistDao.upsert(current.copy(name = name.ifBlank { username }, username = username, password = password))
                if (account != null) settings.setAccountInfo(id, account)
                if (credentialsChanged) {
                    syncPrefs.edit().apply { syncPrefs.all.keys.filter { it.startsWith("$id:") }.forEach(::remove) }.apply()
                    settings.setLastRefreshAt(id, 0L)
                }
            }
        }

    /**
     * Cierra la sesión: borra el catálogo guardado, el progreso, Mi lista y lo organizado de
     * esa cuenta. Las descargas terminadas se conservan (son archivos locales).
     */
    suspend fun logout(id: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            channelDao.deleteByPlaylist(id)
            vodDao.deleteMoviesByPlaylist(id)
            vodDao.deleteSeriesByPlaylist(id)
            vodDao.deleteEpisodesByPlaylist(id)
            progressDao.deleteByPlaylist(id)
            favoriteDao.deleteByPlaylist(id)
            catalogDao.deleteAllCategories(id)
            catalogDao.deleteAllPrefs(id)
            catalogDao.deleteHiddenByPlaylist(id)
            playlistDao.delete(id)
        }
        syncPrefs.edit().apply { syncPrefs.all.keys.filter { it.startsWith("$id:") }.forEach(::remove) }.apply()
        settings.setLastRefreshAt(id, 0L)
        settings.setAccountInfo(id, null)
        detailsCache.evictAll()
    }

    private suspend fun validateAccount(root: String, username: String, password: String): AccountInfo {
        val text = try {
            xtreamApi.loginRaw(apiUrl(root), username, password).string()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw when (e.code()) {
                401, 403 -> AccountException(R.string.account_wrong_credentials)
                in 500..599 -> AccountException(R.string.account_server_down)
                else -> AccountException(R.string.account_server_error, e.code())
            }
        } catch (e: IOException) {
            throw AccountException(R.string.account_no_connection)
        }
        val info = runCatching { json.decodeFromString(XtreamLogin.serializer(), text).userInfo }.getOrNull()
            ?: throw AccountException(R.string.account_wrong_credentials)
        val auth = (info.auth as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull?.trim() == "1") } ?: true
        if (!auth) throw AccountException(R.string.account_wrong_credentials)
        val account = info.toAccountInfo()
        val expires = account.expiresAt
        val status = account.status
        when {
            status.equals("Expired", true) || (expires > 0 && expires * 1000 < System.currentTimeMillis()) ->
                throw AccountException(R.string.account_expired)
            status.equals("Banned", true) -> throw AccountException(R.string.account_banned)
            status.equals("Disabled", true) -> throw AccountException(R.string.account_disabled)
            status.isNotEmpty() && !status.equals("Active", true) ->
                throw AccountException(R.string.account_inactive, status)
        }
        return account
    }

    private fun XtreamLogin.UserInfo.toAccountInfo() = AccountInfo(
        status = status.trim(),
        expiresAt = expDate.asEpochSeconds(),
        maxConnections = maxConnections.asField().toIntOrNull() ?: 0,
        isTrial = (isTrial as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull?.trim() == "1") } ?: false,
        checkedAt = System.currentTimeMillis()
    )

    /** Estado y vencimiento de la cuenta guardados en la última consulta. */
    fun accountInfo(playlistId: String): Flow<AccountInfo?> = settings.accountInfo(playlistId)

    /** Vuelve a pedir los datos de la cuenta sin cortar la actualización si el servidor falla. */
    private suspend fun refreshAccountInfo(p: PlaylistEntity) {
        if (p.type != "XTREAM") return
        val info = runCatching {
            val text = xtreamApi.loginRaw(apiUrl(serverRoot(p.url)), p.username, p.password).string()
            json.decodeFromString(XtreamLogin.serializer(), text).userInfo
        }.getOrNull() ?: return
        settings.setAccountInfo(p.id, info.toAccountInfo())
    }

    /**
     * El servidor cambió desde update.json: las cuentas que estaban en el servidor anterior
     * de la app pasan al nuevo, con sus direcciones de canales, películas y descargas.
     */
    suspend fun applyServerOverride(newUrl: String) = withContext(Dispatchers.IO) {
        if (newUrl.isBlank() || !newUrl.startsWith("http")) return@withContext
        val newRoot = serverRoot(newUrl)
        val oldRoot = serverRoot(settings.serverUrl.first())
        val builtIn = BuildConfig.DEFAULT_SERVER_URL.takeIf { it.isNotBlank() }?.let(::serverRoot)
        if (newRoot != oldRoot) {
            val moving = playlistDao.getAll().filter {
                it.type == "XTREAM" && serverRoot(it.url).let { root -> root == oldRoot || root == builtIn }
            }
            database.withTransaction {
                moving.forEach { p ->
                    val from = serverRoot(p.url)
                    playlistDao.upsert(p.copy(url = newRoot))
                    channelDao.rewriteRoot(p.id, from, newRoot)
                    vodDao.rewriteMovieRoot(p.id, from, newRoot)
                    vodDao.rewriteEpisodeRoot(p.id, from, newRoot)
                    downloadDao.rewriteRoot(p.id, from, newRoot)
                }
            }
        }
        settings.setServerOverride(newUrl.trim())
    }

    // ============================ Actualización del catálogo ============================

    /**
     * Actualización al entrar: lo guardado se ve al instante y esto corre en segundo plano.
     * Sin [force] se respeta la frecuencia de Configuración (por defecto, una vez al día
     * después de las 9:00; ver [refreshDue]).
     * Cada sección (canales, películas, series) es independiente: si una falla se conserva lo guardado.
     *
     * @return secciones que no se pudieron actualizar, con el motivo.
     */
    suspend fun refreshAll(force: Boolean = false): Result<List<UiText>> = withContext(syncDispatcher) {
        runCatching {
            refreshLock.withLock {
                playlistDao.getAll().flatMap { playlist -> refreshIfDue(playlist, force) }
            }
        }.onFailure { logSyncFailure("refresh", it) }
    }

    suspend fun refreshPlaylist(playlistId: String, force: Boolean = true): Result<List<UiText>> = withContext(syncDispatcher) {
        runCatching {
            refreshLock.withLock {
                val p = playlistDao.get(playlistId) ?: error("Playlist no encontrada")
                refreshIfDue(p, force)
            }
        }.onFailure { logSyncFailure("refresh", it) }
    }

    /** True si la lista nunca se descargó completa (primer inicio de sesión). */
    suspend fun needsFirstSync(playlistId: String): Boolean = settings.lastRefreshAt(playlistId) == 0L

    private suspend fun refreshIfDue(p: PlaylistEntity, force: Boolean): List<UiText> {
        val last = settings.lastRefreshAt(p.id)
        if (!force && !refreshDue(last, settings.refreshMode.first())) return emptyList()
        _syncing.value = true
        try {
            val failed = coroutineScope {
                val account = async { refreshAccountInfo(p) }
                refreshPlaylistInternal(p, force).also { account.await() }
            }
            // Una sección que falló y quedó vacía se vuelve a pedir al entrar, sin esperar al próximo corte.
            val emptyAfterFailure = failed.any { sectionCount(p.id, it.section) == 0 }
            if (failed.size < 3 && !emptyAfterFailure) settings.setLastRefreshAt(p.id, System.currentTimeMillis())
            return failed.map { sectionFailed(it.section, it.error!!) }
        } finally {
            _syncing.value = false
        }
    }

    /** @return secciones que no se pudieron actualizar. */
    private suspend fun refreshPlaylistInternal(p: PlaylistEntity, force: Boolean): List<SectionResult> = coroutineScope {
        when (p.type) {
            "M3U" -> listOf(
                prepareSection(SECTION_LIVE) {
                    val body = withRetry { xtreamApi.fetchText(p.url).string() }
                    // Solo la huella: la lista entera puede pesar decenas de MB y no debe seguir viva hasta guardar.
                    val print = textPrint(body)
                    if (!force && unchanged(p.id, "m3u", print, "") && channelDao.count(p.id) > 0) return@prepareSection null
                    val channels = M3uParser.parse(p.id, body)
                    if (channels.isEmpty()) return@prepareSection null
                    PendingSave(
                        save = {
                            channelDao.deleteByPlaylist(p.id)
                            channelDao.upsertAll(channels)
                            saveCategories(p.id, SECTION_LIVE, channels.map { it.groupTitle })
                        },
                        saved = { remember(p.id, "m3u", print, "") }
                    )
                }
            ).let { saveSections(it) }
            else -> {
                suspend fun prepare(section: String) = prepareSection(section) {
                    when (section) {
                        SECTION_LIVE -> prepareLive(p, force)
                        SECTION_MOVIES -> prepareMovies(p, force)
                        else -> prepareSeries(p, force)
                    }
                }
                val first = listOf(SECTION_LIVE, SECTION_MOVIES, SECTION_SERIES).map { async { prepare(it) } }.awaitAll()
                // Si una lista llegó cortada en todos los intentos, una vuelta más ya sin las otras descargas.
                val results = first.map { if (it.error is SerializationException) prepare(it.section) else it }
                saveSections(results)
            }
        }
    }

    /** Sección ya bajada y leída: [save] la escribe en la base y [saved] anota su huella. */
    private class PendingSave(val save: suspend () -> Unit, val saved: () -> Unit)

    private class SectionResult(val section: String, val pending: PendingSave?, val error: Exception?)

    private suspend fun prepareSection(section: String, block: suspend () -> PendingSave?): SectionResult =
        try {
            SectionResult(section, block(), null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logSyncFailure(section, e)
            SectionResult(section, null, e)
        }

    /**
     * Guarda juntas las secciones que cambiaron, en una transacción: Room avisa una sola vez y
     * la pantalla se arma de nuevo una vez al final, sin pasar por una lista vacía.
     *
     * @return secciones que no se pudieron bajar o guardar.
     */
    private suspend fun saveSections(results: List<SectionResult>): List<SectionResult> {
        val failed = results.filter { it.error != null }
        val pending = results.filter { it.pending != null }
        if (pending.isEmpty()) return failed
        return try {
            database.withTransaction { pending.forEach { it.pending!!.save() } }
            pending.forEach { it.pending!!.saved() }
            failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logSyncFailure("save", e)
            failed + pending.map { SectionResult(it.section, null, e) }
        }
    }

    private suspend fun sectionCount(playlistId: String, section: String): Int = when (section) {
        SECTION_LIVE -> channelDao.count(playlistId)
        SECTION_MOVIES -> vodDao.countMovies(playlistId)
        else -> vodDao.countSeries(playlistId)
    }

    private suspend fun prepareLive(p: PlaylistEntity, force: Boolean): PendingSave? = coroutineScope {
        val catsJob = async { fetchOrNull(p, "get_live_categories", CATEGORY_LIST) }
        val streams = fetchAction(p, "get_live_streams", ListSerializer(XtreamStream.serializer()))
        val cats = catsJob.await()
        if (streams.value.isEmpty()) return@coroutineScope null
        // La versión hace que los canales guardados antes de tener catch-up se reescriban una vez.
        val catsPrint = cats?.print?.let { "$it|$LIVE_FIELDS_VERSION" }
        if (!force && catsPrint != null && unchanged(p.id, "live", streams.print, catsPrint) && channelDao.count(p.id) > 0) {
            return@coroutineScope null
        }
        // Si las categorías no llegaron, cada canal conserva el grupo que ya tenía.
        val previous = if (cats == null) {
            channelDao.getByPlaylist(p.id).associate { it.streamKey to it.groupTitle }
        } else emptyMap()
        val names = cats?.value.orEmpty().associate { it.categoryId to it.categoryName }
        val root = serverRoot(p.url)
        val channels = streams.value.mapIndexed { index, s ->
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
                number = index + 1,
                archiveDays = s.archiveDays
            )
        }
        val order = categoryOrder(p.id, SECTION_LIVE, cats?.value) + channels.map { it.groupTitle }
        pendingSave(p.id, "live", streams.print, catsPrint) {
            channelDao.deleteByPlaylist(p.id)
            channelDao.upsertAll(channels)
            saveCategories(p.id, SECTION_LIVE, order)
        }
    }

    private suspend fun prepareMovies(p: PlaylistEntity, force: Boolean): PendingSave? = coroutineScope {
        val catsJob = async { fetchOrNull(p, "get_vod_categories", CATEGORY_LIST) }
        val streams = fetchAction(p, "get_vod_streams", ListSerializer(XtreamVodStream.serializer()))
        val cats = catsJob.await()
        if (streams.value.isEmpty()) return@coroutineScope null
        if (!force && cats != null && unchanged(p.id, "movies", streams.print, cats.print) && vodDao.countMovies(p.id) > 0) {
            return@coroutineScope null
        }
        val previous = if (cats == null) {
            vodDao.getMovies(p.id).associate { it.movieId to it.categoryName }
        } else emptyMap()
        val names = cats?.value.orEmpty().associate { it.categoryId to it.categoryName }
        val root = serverRoot(p.url)
        val movies = streams.value.mapIndexed { index, m ->
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
                position = index,
                tmdbId = m.tmdb.asField().ifEmpty { m.tmdbId.asField() }.takeIf { it.all(Char::isDigit) }.orEmpty(),
                year = m.year.asYear().takeIf { it > 0 } ?: yearFromName(m.name)
            )
        }
        val order = categoryOrder(p.id, SECTION_MOVIES, cats?.value) + movies.map { it.categoryName }
        pendingSave(p.id, "movies", streams.print, cats?.print) {
            vodDao.deleteMoviesByPlaylist(p.id)
            vodDao.upsertMovies(movies)
            saveCategories(p.id, SECTION_MOVIES, order)
        }
    }

    private suspend fun prepareSeries(p: PlaylistEntity, force: Boolean): PendingSave? = coroutineScope {
        val catsJob = async { fetchOrNull(p, "get_series_categories", CATEGORY_LIST) }
        val list = fetchAction(p, "get_series", ListSerializer(XtreamSeries.serializer()))
        val cats = catsJob.await()
        if (list.value.isEmpty()) return@coroutineScope null
        if (!force && cats != null && unchanged(p.id, "series", list.print, cats.print) && vodDao.countSeries(p.id) > 0) {
            return@coroutineScope null
        }
        val previous = if (cats == null) {
            vodDao.getSeriesList(p.id).associate { it.seriesId to it.categoryName }
        } else emptyMap()
        val names = cats?.value.orEmpty().associate { it.categoryId to it.categoryName }
        val series = list.value.mapIndexed { index, s ->
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
                position = index,
                tmdbId = s.tmdb.asField().ifEmpty { s.tmdbId.asField() }.takeIf { it.all(Char::isDigit) }.orEmpty(),
                year = s.releaseDate.asYear().takeIf { it > 0 } ?: s.releaseDateAlt.asYear().takeIf { it > 0 } ?: yearFromName(s.name)
            )
        }
        val order = categoryOrder(p.id, SECTION_SERIES, cats?.value) + series.map { it.categoryName }
        pendingSave(p.id, "series", list.print, cats?.print) {
            vodDao.deleteSeriesByPlaylist(p.id)
            vodDao.upsertSeries(series)
            saveCategories(p.id, SECTION_SERIES, order)
        }
    }

    /**
     * Solo recibe las huellas (no la respuesta): lo bajado del servidor se libera mientras las
     * otras secciones terminan. Sin categorías no se anota la huella y se vuelve a intentar.
     */
    private fun pendingSave(
        playlistId: String,
        section: String,
        print: String,
        catsPrint: String?,
        save: suspend () -> Unit
    ) = PendingSave(save) { if (catsPrint != null) remember(playlistId, section, print, catsPrint) }

    private fun fingerprint(main: String, extra: String): String = "$main|$extra"

    private fun textPrint(text: String): String = "${text.length}:${text.hashCode()}"

    private fun unchanged(playlistId: String, section: String, main: String, extra: String): Boolean =
        syncPrefs.getString("$playlistId:$section", null) == fingerprint(main, extra)

    private fun remember(playlistId: String, section: String, main: String, extra: String) {
        syncPrefs.edit().putString("$playlistId:$section", fingerprint(main, extra)).apply()
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

    /** [print] resume la respuesta (bytes y MD5) para saber si cambió sin guardarla entera. */
    private class Fetched<T>(val print: String, val value: T)

    /**
     * Descarga una acción de player_api.php y la decodifica mientras llega: con listas de
     * decenas de MB no se arma el texto completo en memoria. Reintenta si llega cortada.
     */
    private suspend fun <T> fetchAction(p: PlaylistEntity, action: String, serializer: KSerializer<T>): Fetched<T> =
        withRetry {
            requestSlots.withPermit {
                xtreamApi.action(apiUrl(p.url), p.username, p.password, action).use { body ->
                    val hashing = HashingSource.md5(body.source())
                    var count = 0L
                    val counting = object : ForwardingSource(hashing) {
                        override fun read(sink: Buffer, byteCount: Long): Long =
                            super.read(sink, byteCount).also { if (it > 0) count += it }
                    }
                    val value = json.decodeFromStream(serializer, counting.buffer().inputStream())
                    Fetched("$count:${hashing.hash.hex()}", value)
                }
            }
        }

    private suspend fun <T> fetchOrNull(p: PlaylistEntity, action: String, serializer: KSerializer<T>): Fetched<T>? =
        try {
            fetchAction(p, action, serializer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logSyncFailure(action, e)
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

    private fun sectionFailed(section: String, e: Exception): UiText {
        val label = when (section) {
            SECTION_MOVIES -> R.string.section_movies
            SECTION_SERIES -> R.string.section_series
            else -> R.string.home_refresh_channels
        }
        return uiText(R.string.home_refresh_section_failed, uiText(label), e.friendlyReason())
    }

    /** Solo la clase y el mensaje: las URL de la cuenta llevan usuario y contraseña. */
    private fun logSyncFailure(what: String, e: Throwable) {
        if (e is CancellationException) return
        Log.w(TAG, "$what: ${e.javaClass.simpleName}: ${e.message.orEmpty().lineSequence().first().redactUrls()}")
    }

    private fun String.redactUrls(): String = replace(URL_PATTERN, "<url>")

    private fun Throwable.friendlyReason(): UiText = when (this) {
        is SerializationException -> uiText(R.string.home_refresh_reason_cut)
        is SocketTimeoutException -> uiText(R.string.home_refresh_reason_timeout)
        is HttpException -> uiText(R.string.home_refresh_reason_http, code())
        is IOException ->
            if (message.orEmpty().contains("unexpected end", ignoreCase = true)) uiText(R.string.home_refresh_reason_cut)
            else uiText(R.string.home_refresh_reason_offline)
        else -> message?.let { UiText.Raw(it) } ?: uiText(R.string.home_refresh_reason_unknown)
    }

    // ============================ Organización ============================

    fun categories(playlistId: String): Flow<List<CategoryEntity>> = catalogDao.observeCategories(playlistId)

    fun categoryPrefs(playlistId: String): Flow<List<CategoryPrefEntity>> = catalogDao.observePrefs(playlistId)

    val hiddenItems: Flow<List<HiddenItemEntity>> = catalogDao.observeHidden()

    suspend fun saveCategoryPrefs(prefs: List<CategoryPrefEntity>) =
        withContext(Dispatchers.IO) { catalogDao.upsertPrefs(prefs) }

    suspend fun categoryPrefsSnapshot(playlistId: String): List<CategoryPrefEntity> =
        withContext(Dispatchers.IO) { catalogDao.getPrefs(playlistId) }

    suspend fun categoriesSnapshot(playlistId: String, section: String): List<CategoryEntity> =
        withContext(Dispatchers.IO) { catalogDao.getCategories(playlistId, section) }

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
                    .let { d -> if (d.tmdbId.isEmpty()) d.copy(tmdbId = movie.tmdbId) else d }
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
                    database.withTransaction {
                        vodDao.deleteEpisodesForSeries(series.id)
                        vodDao.upsertEpisodes(episodes)
                    }
                }
                val details = XtreamInfoParser.series(raw).let { d ->
                    d.copy(
                        plot = d.plot.ifEmpty { series.plot },
                        tmdbId = d.tmdbId.ifEmpty { series.tmdbId }
                    )
                }
                detailsCache.put(series.id, details)
                details
            }
        }

    fun cachedDetails(contentKey: String): ContentDetails? = detailsCache.get(contentKey)

    fun putDetails(contentKey: String, details: ContentDetails) {
        detailsCache.put(contentKey, details)
    }

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

    companion object {
        /** Raíz del servidor, sin player_api.php, siempre con / final. */
        internal fun serverRoot(raw: String): String {
            var value = raw.trim().trimEnd('/')
            if (value.endsWith("/player_api.php", ignoreCase = true)) {
                value = value.substring(0, value.length - "/player_api.php".length).trimEnd('/')
            }
            return "$value/"
        }

        internal fun apiUrl(raw: String): String = serverRoot(raw) + "player_api.php"

        const val SECTION_LIVE = "LIVE"
        const val SECTION_MOVIES = "MOVIES"
        const val SECTION_SERIES = "SERIES"
        private const val MAX_ATTEMPTS = 4
        /** Sube cuando ChannelEntity guarda un dato nuevo del servidor. */
        private const val LIVE_FIELDS_VERSION = 2
        private const val TAG = "CatalogSync"
        private val URL_PATTERN = Regex("""\w+://\S+""")
        private val CATEGORY_LIST = ListSerializer(XtreamCategory.serializer())

        /** El proveedor publica lo nuevo temprano: después de esta hora vale la pena volver a bajar. */
        const val DAILY_CUTOFF_HOUR = 9
        private const val EVENING_CUTOFF_HOUR = 21

        /**
         * Toca actualizar según [mode]: siempre, o si la última fue antes del corte más reciente
         * (las 9:00 de hoy o de ayer; con dos veces al día, también las 21:00).
         */
        fun refreshDue(last: Long, mode: Int, now: Long = System.currentTimeMillis()): Boolean {
            if (last <= 0L || mode == SettingsRepository.REFRESH_ALWAYS) return true
            return last < lastCutoff(mode, now)
        }

        private fun lastCutoff(mode: Int, now: Long): Long {
            val hours = if (mode == SettingsRepository.REFRESH_TWICE_DAILY) {
                listOf(EVENING_CUTOFF_HOUR, DAILY_CUTOFF_HOUR)
            } else listOf(DAILY_CUTOFF_HOUR)
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            for (hour in hours) {
                cal.set(Calendar.HOUR_OF_DAY, hour)
                if (cal.timeInMillis <= now) return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
            cal.set(Calendar.HOUR_OF_DAY, hours.first())
            return cal.timeInMillis
        }
    }
}
