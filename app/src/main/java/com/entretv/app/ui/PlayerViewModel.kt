package com.entretv.app.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entretv.app.R
import com.entretv.app.data.download.DownloadRepository
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.EpgEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.VodMovieEntity
import com.entretv.app.data.profile.ProfileSession
import com.entretv.app.data.repo.GuideRepository
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.PlayerPrefs
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.di.ApplicationScope
import com.entretv.app.player.PlayerManager
import com.entretv.app.ui.navigation.PlayerRoute
import com.entretv.app.ui.player.PlayerSettingsActions
import com.entretv.app.ui.player.SLEEP_AT_END
import com.entretv.app.ui.player.TrackOption
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.entretv.app.data.local.FavoriteEntity
import java.text.Normalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Categoría en vivo del navegador de canales: [raw] es la clave, [label] lo que se muestra (vacío si no tiene nombre). */
data class LiveCategory(val raw: String, val label: String, val count: Int)

/** Contenido reproducible genérico. */
sealed interface Playable {
    val contentKey: String
    val streamUrl: String
    val title: String
    val subtitle: String
    val imageUrl: String
    val kind: String

    /** [channel] es la copia para mostrar: nombre y grupo ya limpios si el ajuste está activo. */
    data class LiveChannel(val channel: ChannelEntity) : Playable {
        override val contentKey get() = channel.id
        override val streamUrl get() = channel.streamUrl
        override val title get() = channel.name
        override val subtitle get() = channel.groupTitle
        override val imageUrl get() = channel.logoUrl
        override val kind get() = "LIVE"
    }

    data class Movie(
        val movie: VodMovieEntity,
        override val title: String = movie.name,
        override val subtitle: String = movie.categoryName
    ) : Playable {
        override val contentKey get() = movie.id
        override val streamUrl get() = movie.streamUrl
        override val imageUrl get() = movie.icon
        override val kind get() = "MOVIE"
    }

    data class Episode(val episode: EpisodeEntity, val seriesTitle: String, val seriesCover: String = "") : Playable {
        override val contentKey get() = episode.id   // playlistId:seriesId#epId
        override val streamUrl get() = episode.streamUrl
        override val title get() = seriesTitle
        override val subtitle get() = "T${episode.season}:E${episode.episodeNum} · ${episode.title}"
        override val imageUrl get() = episode.imageUrl.ifBlank { seriesCover }
        override val kind get() = "EPISODE"
    }

    /** Programa pasado de un canal con archivo. Sin data class: la URL lleva credenciales y no debe salir en toString. */
    class CatchUp(
        val channel: ChannelEntity,
        val start: Long,
        override val title: String,
        override val streamUrl: String
    ) : Playable {
        override val contentKey get() = "${channel.id}@$start"
        override val subtitle get() = channel.name
        override val imageUrl get() = channel.logoUrl
        override val kind get() = "CATCHUP"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: PlaylistRepository,
    private val settings: SettingsRepository,
    private val parental: ParentalControl,
    private val downloads: DownloadRepository,
    private val guide: GuideRepository,
    private val profileSession: ProfileSession,
    @ApplicationScope private val appScope: CoroutineScope,
    val playerManager: PlayerManager
) : ViewModel(), PlayerSettingsActions {

    private val route: PlayerRoute = savedStateHandle.toRoute()

    private var cleanNames = true

    private fun clean(raw: String): String = TitleCleaner.clean(raw, cleanNames)

    private fun display(channel: ChannelEntity): ChannelEntity =
        if (!cleanNames) channel else channel.copy(name = clean(channel.name), groupTitle = clean(channel.groupTitle))

    /**
     * Canales que se pueden zapear: sin los ocultos, sin categorías ocultas y, con el PIN
     * puesto, sin las de adultos. Devuelve las entidades tal cual (grupo sin limpiar).
     */
    private suspend fun zappableChannels(playlistId: String): List<ChannelEntity> {
        val all = repository.channelSnapshot(playlistId)
        val hidden = repository.hiddenKeys()
        val locked = parental.locked.first()
        val kids = parental.kids.value
        val prefs = repository.categoryPrefsSnapshot(playlistId)
            .filter { it.section == PlaylistRepository.SECTION_LIVE }
            .associateBy { it.name }
        val excluded = HashMap<String, Boolean>()
        return all.filter { ch ->
            ch.id !in hidden && !(kids && ParentalControl.looksAdult(ch.name)) && !excluded.getOrPut(ch.groupTitle) {
                val pref = prefs[ch.groupTitle]
                pref?.hidden == true || (locked && ParentalControl.isAdult(ch.groupTitle, pref))
            }
        }
    }

    private val _playable = MutableStateFlow<Playable?>(null)
    val playable: StateFlow<Playable?> = _playable

    private val _epg = MutableStateFlow<List<EpgEntity>>(emptyList())
    val epg: StateFlow<List<EpgEntity>> = _epg

    /** Episodios de la serie actual (para auto-next y zapping de episodios). */
    private val _episodes = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    val episodes: StateFlow<List<EpisodeEntity>> = _episodes

    /** Episodio que sigue al actual, para el botón "Siguiente episodio". */
    private val _nextEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val nextEpisode: StateFlow<EpisodeEntity?> = _nextEpisode

    /** El usuario canceló la cuenta regresiva: al terminar este episodio no pasa solo al siguiente. */
    private val _autoNextCanceled = MutableStateFlow(false)
    val autoNextCanceled: StateFlow<Boolean> = _autoNextCanceled

    /** Fin de la intro aprendido para la serie actual; null = no se sabe y se salta [DEFAULT_INTRO_SKIP_MS]. */
    private val _introEndMs = MutableStateFlow<Long?>(null)
    val introEndMs: StateFlow<Long?> = _introEndMs

    /** La intro de este episodio ya quedó atrás (botón usado o adelantó a mano). */
    private val _introDone = MutableStateFlow(false)
    val introDone: StateFlow<Boolean> = _introDone
    /** Dónde empezó la tanda de saltos en curso; -1 sin tanda. */
    private var introSeekFrom = -1L
    private var introLearnJob: Job? = null

    /** Canales del mismo grupo que el actual (lista lateral y zapping). */
    private val _groupChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val groupChannels: StateFlow<List<ChannelEntity>> = _groupChannels

    /** Mensaje puntual: "Reanudado en 12:34" / "Siguiente episodio…". */
    private val _notice = MutableStateFlow<UiText?>(null)
    val notice: StateFlow<UiText?> = _notice
    private var noticeJob: Job? = null

    val playerPrefs: StateFlow<PlayerPrefs> = settings.playerPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerPrefs())

    val showClock: StateFlow<Boolean> = settings.showClock
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Minutos del temporizador; 0 apagado, [SLEEP_AT_END] al terminar el contenido. */
    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes
    private val _sleepEndsAt = MutableStateFlow(0L)
    val sleepEndsAt: StateFlow<Long> = _sleepEndsAt
    private var sleepJob: Job? = null

    private var saveJob: Job? = null
    private var lastSavedMs = -1L

    private fun showNotice(message: UiText, durationMs: Long = 2_500) {
        noticeJob?.cancel()
        _notice.value = message
        noticeJob = viewModelScope.launch {
            delay(durationMs)
            _notice.value = null
        }
    }

    // ============================ Panel de ajustes ============================

    // Audio y subtítulos elegidos aquí valen solo para este video; el siguiente arranca con Configuración.
    override fun selectAudio(option: TrackOption?) {
        if (option == null) playerManager.disableAudio()
        else playerManager.selectTrack(option.group, option.index)
    }

    override fun selectSubtitle(option: TrackOption?) {
        if (option == null) playerManager.disableSubtitles()
        else playerManager.selectTrack(option.group, option.index)
    }

    override fun selectVideo(option: TrackOption?) {
        if (option == null) playerManager.autoVideo()
        else playerManager.selectVideoTrack(option.group, option.index)
    }

    override fun disableVideo() {
        playerManager.disableVideo()
    }

    override fun repairVideo() {
        val software = playerManager.repairVideo()
        showNotice(uiText(if (software) R.string.player_notice_repair_software else R.string.player_notice_repair_hardware))
    }

    override fun setSubtitleSize(size: Int) {
        viewModelScope.launch { settings.setSubtitleSize(size) }
    }

    override fun setSubtitleColor(color: Int) {
        viewModelScope.launch { settings.setSubtitleColor(color) }
    }

    override fun setSubtitleBackground(background: Int) {
        viewModelScope.launch { settings.setSubtitleBackground(background) }
    }

    override fun setSubtitlePosition(position: Int) {
        viewModelScope.launch { settings.setSubtitlePosition(position) }
    }

    override fun setResizeMode(mode: Int) {
        viewModelScope.launch { settings.setResizeMode(mode) }
    }

    override fun setSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    override fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _sleepMinutes.value = minutes
        _sleepEndsAt.value = 0L
        when {
            minutes > 0 -> {
                _sleepEndsAt.value = System.currentTimeMillis() + minutes * 60_000L
                sleepJob = viewModelScope.launch {
                    delay(minutes * 60_000L)
                    sleepNow()
                }
                showNotice(uiPlural(R.plurals.player_notice_sleep_in, minutes, minutes))
            }
            minutes == SLEEP_AT_END -> showNotice(uiText(R.string.player_notice_sleep_at_end))
            else -> showNotice(uiText(R.string.player_notice_sleep_off))
        }
    }

    private fun sleepNow() {
        playerManager.player.pause()
        _sleepMinutes.value = 0
        _sleepEndsAt.value = 0L
        showNotice(uiText(R.string.player_notice_sleep_paused), 5_000)
    }

    // ============================ Navegador de canales ============================

    /** Canales que se pueden zapear de la lista actual, con el grupo sin limpiar. */
    private val _allChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    private var loadedPlaylist: String? = null
    private val _currentGroup = MutableStateFlow("")
    /** Posición de cada categoría en vivo, la misma que en la sección En vivo de Inicio. */
    private val _categoryOrder = MutableStateFlow<Map<String, Int>>(emptyMap())

    val liveCategories: StateFlow<List<LiveCategory>> = combine(_allChannels, _categoryOrder) { all, order ->
        all.groupingBy { it.groupTitle }.eachCount()
            .map { (raw, count) -> LiveCategory(raw, clean(raw), count) }
            .sortedBy { order[it.raw] ?: Int.MAX_VALUE }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Mismo criterio que Inicio: primero el orden que armó el usuario, después el del servidor;
     * sin orden propio y con A-Z como orden por defecto, alfabético.
     */
    private suspend fun liveCategoryOrder(playlistId: String): Map<String, Int> {
        val prefs = repository.categoryPrefsSnapshot(playlistId)
            .filter { it.section == PlaylistRepository.SECTION_LIVE }
            .associateBy { it.name }
        val server = repository.categoriesSnapshot(playlistId, PlaylistRepository.SECTION_LIVE)
            .associate { it.name to it.position }
        val names = (server.keys + prefs.keys).distinct()
        val userOrdered = prefs.values.any { it.userPosition != null }
        val sorted = if (!userOrdered && settings.catalogPrefs.first().defaultSort == SortOrder.AZ.name) {
            names.sortedBy { searchKey(clean(it)) }
        } else {
            names.sortedWith(compareBy({ prefs[it]?.userPosition ?: Int.MAX_VALUE }, { server[it] ?: Int.MAX_VALUE }))
        }
        return sorted.withIndex().associate { it.value to it.index }
    }

    /** Categoría abierta en el navegador; null = la del canal que se está viendo. */
    private val _browseCategory = MutableStateFlow<String?>(null)
    val browseCategory: StateFlow<String> = combine(_browseCategory, _currentGroup) { chosen, current -> chosen ?: current }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _browseQuery = MutableStateFlow("")
    val browseQuery: StateFlow<String> = _browseQuery

    val browseChannels: StateFlow<List<ChannelEntity>> =
        combine(_allChannels, browseCategory, _browseQuery) { all, category, query ->
            val q = searchKey(query)
            if (q.isNotEmpty()) all.filter { searchKey(it.name).contains(q) }.take(300).map(::display)
            else all.filter { it.groupTitle == category }.map(::display)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun browse(category: String) {
        _browseQuery.value = ""
        _browseCategory.value = category
    }

    fun searchChannels(query: String) { _browseQuery.value = query }

    /** Al abrir el panel: vuelve a la categoría del canal actual, sin búsqueda. */
    fun resetBrowse() {
        _browseCategory.value = null
        _browseQuery.value = ""
    }

    private fun searchKey(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    /** Programa actual de un canal según la guía guardada (sin ir a la red). */
    suspend fun nowPlaying(channel: ChannelEntity): String? =
        repository.upcomingEpg(channel, limit = 1).firstOrNull()?.title

    fun playChannel(channel: ChannelEntity) {
        val current = _playable.value
        if (current is Playable.LiveChannel && current.channel.id == channel.id) return
        viewModelScope.launch { startPlayable(Playable.LiveChannel(channel)) }
    }

    /** "Siguiente capítulo": el actual cuenta como visto aunque no haya llegado al final. */
    fun playNextEpisode() {
        val p = _playable.value as? Playable.Episode ?: return
        val next = _nextEpisode.value ?: return
        viewModelScope.launch {
            markCompleted(p)
            startPlayable(p.copy(episode = next))
        }
    }

    private suspend fun markCompleted(p: Playable) {
        if (p is Playable.CatchUp) return
        val duration = playerManager.durationMs.value.takeIf { it > 0 }
            ?: playerManager.player.duration.takeIf { it > 0 }
            ?: (p as? Playable.Episode)?.episode?.durationSecs?.takeIf { it > 0 }?.let { it * 1_000L }
            ?: 1L
        repository.saveProgress(
            PlaybackProgressEntity(
                contentKey = p.contentKey, kind = p.kind,
                playlistId = p.contentKey.substringBefore(":"), refId = p.contentKey,
                title = p.title, imageUrl = p.imageUrl, subtitle = p.subtitle,
                positionMs = duration,
                durationMs = duration
            )
        )
    }

    /** Canal, película o serie (en episodios) del contenido actual, para "Mi lista". */
    val isFavorite: StateFlow<Boolean> = _playable
        .flatMapLatest { p -> if (p == null) flowOf(false) else repository.isFavorite(p.favoriteKey()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun Playable.favoriteKey(): String = when (this) {
        is Playable.Episode -> episode.seriesKey
        is Playable.CatchUp -> channel.id
        else -> contentKey
    }

    fun toggleFavorite() {
        val p = _playable.value ?: return
        viewModelScope.launch {
            val favorite = when (p) {
                is Playable.LiveChannel -> FavoriteEntity(p.channel.id, "LIVE", p.channel.playlistId, p.title, p.imageUrl)
                is Playable.CatchUp -> FavoriteEntity(p.channel.id, "LIVE", p.channel.playlistId, p.channel.name, p.imageUrl)
                is Playable.Movie -> FavoriteEntity(p.movie.id, "MOVIE", p.movie.playlistId, p.title, p.imageUrl)
                is Playable.Episode -> {
                    val series = repository.seriesById(p.episode.seriesKey)
                    FavoriteEntity(
                        p.episode.seriesKey, "SERIES", p.episode.seriesKey.substringBefore(":"),
                        series?.name?.let(::clean) ?: p.title, series?.cover.orEmpty()
                    )
                }
            }
            val added = repository.toggleFavorite(favorite)
            showNotice(
                uiText(if (added) R.string.player_notice_added_to_list else R.string.player_notice_removed_from_list),
                2_000
            )
        }
    }

    init {
        playerManager.onEnded = { onContentEnded() }
        viewModelScope.launch { load(route.contentKey, route.kind) }
        viewModelScope.launch {
            playerManager.isPlayingFlow.first { it }
            profileSession.markPlayed()
        }
    }

    private suspend fun load(contentKey: String, kind: String) {
        cleanNames = settings.catalogPrefs.first().cleanNames
        when (kind) {
            "LIVE" -> repository.channelById(contentKey)?.let { startPlayable(Playable.LiveChannel(display(it))) }
            "CATCHUP" -> {
                val channel = repository.channelById(contentKey) ?: return
                val url = guide.catchupUrl(channel, route.startSec, route.endSec)
                if (url == null) {
                    showNotice(uiText(R.string.player_catchup_failed), 5_000)
                    return
                }
                startPlayable(Playable.CatchUp(display(channel), route.startSec, route.title.ifBlank { channel.name }, url))
            }
            "MOVIE" -> {
                val movie = repository.movieById(contentKey)
                if (movie != null) {
                    startPlayable(Playable.Movie(movie, clean(movie.name), clean(movie.categoryName)))
                } else {
                    // Descargada pero ya no figura en la lista del servidor.
                    downloads.get(contentKey)?.let { startPlayable(Playable.Movie(it.asMovie())) }
                }
            }
            "EPISODE" -> {
                val ep = repository.episodeById(contentKey)
                if (ep != null) {
                    val series = repository.seriesById(ep.seriesKey)
                    _episodes.value = repository.episodeSnapshot(ep.seriesKey)
                    val seriesTitle = series?.name?.let(::clean)
                        ?: AppLanguage.wrap(appContext).getString(R.string.player_series_fallback)
                    startPlayable(Playable.Episode(ep, seriesTitle, series?.cover.orEmpty()))
                } else {
                    downloads.get(contentKey)?.let { startPlayable(Playable.Episode(it.asEpisode(), it.title, it.imageUrl)) }
                }
            }
        }
    }

    private fun DownloadEntity.asMovie() = VodMovieEntity(
        id = contentKey, playlistId = playlistId, movieId = "", name = title, icon = imageUrl,
        categoryId = "", categoryName = subtitle, streamUrl = sourceUrl
    )

    private fun DownloadEntity.asEpisode() = EpisodeEntity(
        id = contentKey, seriesKey = seriesKey, episodeId = "", season = season, episodeNum = episodeNum,
        title = subtitle.substringAfter(" · "), streamUrl = sourceUrl, imageUrl = imageUrl
    )

    private suspend fun startPlayable(p: Playable) {
        _playable.value = p
        _autoNextCanceled.value = false
        _introDone.value = false
        introLearnJob?.cancel()
        introSeekFrom = -1L
        _introEndMs.value = (p as? Playable.Episode)?.episode?.seriesKey
            ?.takeIf { it.isNotEmpty() }?.let { settings.introEnd(it) }
        val saved = if (p is Playable.CatchUp) null else repository.progressFor(p.contentKey)
        val resumeAt = saved?.positionMs?.takeIf { it > 30_000 && saved.durationMs > 0 && !saved.isCompleted } ?: 0L
        val url = if (p.kind == "LIVE" || p is Playable.CatchUp) p.streamUrl else downloads.localUri(p.contentKey) ?: p.streamUrl

        playerManager.play(
            url, p.title, p.subtitle,
            startPositionMs = resumeAt,
            isLive = p.kind == "LIVE",
            contentKey = p.contentKey
        )

        if (resumeAt > 0L) showNotice(uiText(R.string.player_notice_resumed, formatMs(resumeAt)), 4_000)

        _nextEpisode.value = (p as? Playable.Episode)?.let { ep ->
            val list = _episodes.value
            list.getOrNull(list.indexOfFirst { it.id == ep.episode.id } + 1)
        }
        startProgressLoop(p)
        if (p is Playable.LiveChannel) {
            if (loadedPlaylist != p.channel.playlistId) {
                _categoryOrder.value = liveCategoryOrder(p.channel.playlistId)
                _allChannels.value = zappableChannels(p.channel.playlistId)
                loadedPlaylist = p.channel.playlistId
            }
            val rawGroup = repository.channelById(p.channel.id)?.groupTitle ?: p.channel.groupTitle
            _currentGroup.value = rawGroup
            val group = _groupChannels.value
            if (group.none { it.id == p.channel.id }) {
                _groupChannels.value = _allChannels.value.filter { it.groupTitle == rawGroup }.map(::display)
            }
            _epg.value = repository.upcomingEpg(p.channel)
            repository.refreshEpg(p.channel)
            if (_playable.value == p) _epg.value = repository.upcomingEpg(p.channel)
        }
    }

    /** Guarda el minuto cada 5 segundos mientras reproduce VOD. */
    private fun startProgressLoop(p: Playable) {
        saveJob?.cancel()
        if (p.kind == "LIVE" || p is Playable.CatchUp) return
        lastSavedMs = -1L
        saveJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                savePosition(p)
            }
        }
    }

    private suspend fun savePosition(p: Playable) {
        positionSnapshot(p)?.let { repository.saveProgress(it) }
    }

    /** Lee el reproductor (solo desde el hilo principal); null si no hay minuto nuevo que guardar. */
    private fun positionSnapshot(p: Playable): PlaybackProgressEntity? {
        if (p.kind == "LIVE" || p is Playable.CatchUp) return null
        val position = playerManager.player.currentPosition.coerceAtLeast(0L)
        val duration = playerManager.durationMs.value.takeIf { it > 0 }
            ?: playerManager.player.duration.coerceAtLeast(0L)
        if (position <= 0L) return null
        if (position == lastSavedMs) return null
        lastSavedMs = position
        return PlaybackProgressEntity(
            contentKey = p.contentKey,
            kind = p.kind,
            playlistId = p.let { key -> key.contentKey.substringBefore(":") },
            refId = p.contentKey,
            title = p.title,
            imageUrl = p.imageUrl,
            subtitle = p.subtitle,
            positionMs = position,
            durationMs = if (duration > 0) duration else 0L
        )
    }

    /** Al terminar un episodio: marca 100% y pasa automáticamente al siguiente (estilo Netflix). */
    private fun onContentEnded() {
        val p = _playable.value ?: return
        viewModelScope.launch {
            if (p.kind != "LIVE") markCompleted(p)
            if (_sleepMinutes.value == SLEEP_AT_END) {
                sleepNow()
                return@launch
            }
            if (p is Playable.Episode && !_autoNextCanceled.value) {
                val next = _nextEpisode.value
                if (next != null) {
                    // Con duración conocida la tarjeta ya hizo la cuenta regresiva; si no, se avisa antes.
                    if (playerManager.durationMs.value <= 0) {
                        showNotice(
                            uiText(R.string.player_notice_up_next, uiText(R.string.player_episode_code, next.season, next.episodeNum))
                        )
                        delay(2_500)
                    }
                    if (_playable.value == p && !_autoNextCanceled.value) startPlayable(p.copy(episode = next))
                }
            }
        }
    }

    fun cancelAutoNext() {
        _autoNextCanceled.value = true
    }

    /** Hasta el fin de la intro aprendido o, si no se sabe, [DEFAULT_INTRO_SKIP_MS] más adelante. */
    fun skipIntro() {
        if (_playable.value !is Playable.Episode) return
        val position = playerManager.player.currentPosition.coerceAtLeast(0L)
        val target = _introEndMs.value?.takeIf { it > position } ?: (position + DEFAULT_INTRO_SKIP_MS)
        val duration = playerManager.durationMs.value
        _introDone.value = true
        playerManager.player.seekTo(if (duration > 0) target.coerceAtMost(duration - 1_000) else target)
        viewModelScope.launch { _playable.value?.let { savePosition(it) } }
    }

    /**
     * Una tanda de saltos (adelantar varias veces, corregir hacia atrás) que empieza en los
     * primeros minutos del episodio y termina más adelante, sin pasar de [INTRO_MAX_END_MS],
     * marca el fin de la intro de esa serie. Se toma donde quedó al dejar de saltar.
     */
    private fun learnIntroEnd(from: Long, to: Long) {
        val p = _playable.value as? Playable.Episode ?: return
        val seriesKey = p.episode.seriesKey.takeIf { it.isNotEmpty() } ?: return
        if (introSeekFrom < 0) introSeekFrom = from
        introLearnJob?.cancel()
        introLearnJob = viewModelScope.launch {
            delay(INTRO_SEEK_SETTLE_MS)
            val start = introSeekFrom
            introSeekFrom = -1L
            if (_playable.value != p || start >= INTRO_WINDOW_MS || to <= start) return@launch
            if (to < INTRO_SHOW_FROM_MS || to > INTRO_MAX_END_MS) return@launch
            _introDone.value = true
            _introEndMs.value = to
            settings.setIntroEnd(seriesKey, to)
        }
    }

    /** Zapping: canales en vivo, episodios dentro de la serie. */
    fun zap(delta: Int) {
        val p = _playable.value ?: return
        viewModelScope.launch {
            savePosition(p)
            when (p) {
                is Playable.LiveChannel -> {
                    val list = _groupChannels.value.takeIf { it.size > 1 }
                        ?: zappableChannels(p.channel.playlistId).map(::display)
                    if (list.size < 2) return@launch
                    val idx = list.indexOfFirst { it.id == p.channel.id }
                    val next = list[(idx + delta + list.size) % list.size]
                    startPlayable(Playable.LiveChannel(next))
                }
                is Playable.Episode -> {
                    val list = _episodes.value
                    if (list.size < 2) return@launch
                    val idx = list.indexOfFirst { it.id == p.episode.id }
                    val next = list[(idx + delta + list.size) % list.size]
                    startPlayable(p.copy(episode = next))
                }
                is Playable.Movie, is Playable.CatchUp -> Unit
            }
        }
    }

    fun seekTo(positionMs: Long) {
        learnIntroEnd(playerManager.player.currentPosition.coerceAtLeast(0L), positionMs)
        playerManager.player.seekTo(positionMs)
        viewModelScope.launch { _playable.value?.let { savePosition(it) } }
    }

    override fun onCleared() {
        // viewModelScope ya está cancelado aquí. El minuto se lee antes de stop() y en este hilo;
        // la escritura en Room va al ámbito de la app para no trabar la salida del reproductor.
        val current = _playable.value
        playerManager.onEnded = null
        val progress = current?.let(::positionSnapshot)
        if (progress != null) {
            appScope.launch(NonCancellable) { repository.saveProgress(progress) }
        }
        if (!route.keepPlaying || current !is Playable.LiveChannel) playerManager.stop()
        super.onCleared()
    }

    companion object {
        private val DIACRITICS = Regex("\\p{Mn}+")

        /** "Saltar intro" aparece desde aquí… */
        const val INTRO_SHOW_FROM_MS = 5_000L
        /** …hasta aquí si no se conoce el fin de la intro de la serie. */
        const val INTRO_WINDOW_MS = 180_000L
        /** Se oculta un poco antes del fin aprendido. */
        const val INTRO_END_MARGIN_MS = 2_000L
        /** Más allá de esto un salto ya no se toma como fin de la intro. */
        private const val INTRO_MAX_END_MS = 300_000L
        private const val DEFAULT_INTRO_SKIP_MS = 85_000L
        private const val INTRO_SEEK_SETTLE_MS = 3_000L

        fun formatMs(ms: Long): String {
            val totalSec = ms / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
