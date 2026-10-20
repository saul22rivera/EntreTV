package com.entretv.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entretv.app.data.download.DownloadRepository
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.EpgEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.VodMovieEntity
import androidx.media3.common.C
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.PlayerPrefs
import com.entretv.app.data.settings.SUBTITLES_OFF
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.player.PlayerManager
import com.entretv.app.ui.navigation.PlayerRoute
import com.entretv.app.ui.player.PlayerSettingsActions
import com.entretv.app.ui.player.SLEEP_AT_END
import com.entretv.app.ui.player.TrackOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.entretv.app.data.local.FavoriteEntity
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

/** Categoría en vivo del navegador de canales: [raw] es la clave, [label] lo que se muestra. */
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
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val settings: SettingsRepository,
    private val parental: ParentalControl,
    private val downloads: DownloadRepository,
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
        val prefs = repository.categoryPrefsSnapshot(playlistId)
            .filter { it.section == PlaylistRepository.SECTION_LIVE }
            .associateBy { it.name }
        val excluded = HashMap<String, Boolean>()
        return all.filter { ch ->
            ch.id !in hidden && !excluded.getOrPut(ch.groupTitle) {
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

    /** Canales del mismo grupo que el actual (lista lateral y zapping). */
    private val _groupChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val groupChannels: StateFlow<List<ChannelEntity>> = _groupChannels

    /** Mensaje puntual: "Reanudado en 12:34" / "Siguiente episodio…". */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    private var noticeJob: Job? = null

    val playerPrefs: StateFlow<PlayerPrefs> = settings.playerPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerPrefs())

    /** Minutos del temporizador; 0 apagado, [SLEEP_AT_END] al terminar el contenido. */
    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes
    private val _sleepEndsAt = MutableStateFlow(0L)
    val sleepEndsAt: StateFlow<Long> = _sleepEndsAt
    private var sleepJob: Job? = null

    private var saveJob: Job? = null
    private var lastSavedMs = -1L

    private fun showNotice(message: String, durationMs: Long = 2_500) {
        noticeJob?.cancel()
        _notice.value = message
        noticeJob = viewModelScope.launch {
            delay(durationMs)
            _notice.value = null
        }
    }

    // ============================ Panel de ajustes ============================

    override fun selectAudio(option: TrackOption) {
        playerManager.selectTrack(option.group, option.index)
        option.format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }?.let { lang ->
            viewModelScope.launch { settings.setAudioLanguage(lang) }
        }
    }

    override fun selectSubtitle(option: TrackOption?) {
        if (option == null) {
            playerManager.disableSubtitles()
            viewModelScope.launch { settings.setSubtitleLanguage(SUBTITLES_OFF) }
            return
        }
        playerManager.selectTrack(option.group, option.index)
        val lang = option.format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        viewModelScope.launch { settings.setSubtitleLanguage(lang.orEmpty()) }
    }

    override fun selectQuality(option: TrackOption?) {
        if (option == null) playerManager.autoQuality()
        else playerManager.selectTrack(option.group, option.index)
    }

    override fun setSubtitleSize(size: Int) {
        viewModelScope.launch { settings.setSubtitleSize(size) }
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
                showNotice("Se pausará en $minutes minutos")
            }
            minutes == SLEEP_AT_END -> showNotice("Se pausará al terminar")
            else -> showNotice("Temporizador desactivado")
        }
    }

    private fun sleepNow() {
        playerManager.player.pause()
        _sleepMinutes.value = 0
        _sleepEndsAt.value = 0L
        showNotice("Temporizador: reproducción en pausa", 5_000)
    }

    // ============================ Navegador de canales ============================

    /** Canales que se pueden zapear de la lista actual, con el grupo sin limpiar. */
    private val _allChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    private var loadedPlaylist: String? = null
    private val _currentGroup = MutableStateFlow("")

    val liveCategories: StateFlow<List<LiveCategory>> = _allChannels
        .map { all ->
            all.groupingBy { it.groupTitle }.eachCount().map { (raw, count) ->
                LiveCategory(raw, clean(raw).ifBlank { "Sin categoría" }, count)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        else -> contentKey
    }

    fun toggleFavorite() {
        val p = _playable.value ?: return
        viewModelScope.launch {
            val favorite = when (p) {
                is Playable.LiveChannel -> FavoriteEntity(p.channel.id, "LIVE", p.channel.playlistId, p.title, p.imageUrl)
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
            showNotice(if (added) "Agregado a Mi lista" else "Quitado de Mi lista", 2_000)
        }
    }

    init {
        playerManager.onEnded = { onContentEnded() }
        viewModelScope.launch { load(route.contentKey, route.kind) }
    }

    private suspend fun load(contentKey: String, kind: String) {
        cleanNames = settings.catalogPrefs.first().cleanNames
        when (kind) {
            "LIVE" -> repository.channelById(contentKey)?.let { startPlayable(Playable.LiveChannel(display(it))) }
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
                    startPlayable(Playable.Episode(ep, series?.name?.let(::clean) ?: "Serie", series?.cover.orEmpty()))
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
        val saved = repository.progressFor(p.contentKey)
        val resumeAt = saved?.positionMs?.takeIf { it > 30_000 && saved.durationMs > 0 && !saved.isCompleted } ?: 0L
        val url = if (p.kind == "LIVE") p.streamUrl else downloads.localUri(p.contentKey) ?: p.streamUrl

        playerManager.play(
            url, p.title, p.subtitle,
            startPositionMs = resumeAt,
            isLive = p.kind == "LIVE"
        )

        if (resumeAt > 0L) showNotice("Reanudado en ${formatMs(resumeAt)}", 4_000)

        _nextEpisode.value = (p as? Playable.Episode)?.let { ep ->
            val list = _episodes.value
            list.getOrNull(list.indexOfFirst { it.id == ep.episode.id } + 1)
        }
        startProgressLoop(p)
        if (p is Playable.LiveChannel) {
            if (loadedPlaylist != p.channel.playlistId) {
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
        if (p.kind == "LIVE") return
        lastSavedMs = -1L
        saveJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                savePosition(p)
            }
        }
    }

    private suspend fun savePosition(p: Playable) {
        val position = playerManager.player.currentPosition.coerceAtLeast(0L)
        val duration = playerManager.durationMs.value.takeIf { it > 0 }
            ?: playerManager.player.duration.coerceAtLeast(0L)
        if (position <= 0L) return
        if (position == lastSavedMs) return
        lastSavedMs = position
        repository.saveProgress(
            PlaybackProgressEntity(
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
            if (p is Playable.Episode) {
                val next = _nextEpisode.value
                if (next != null) {
                    showNotice("A continuación: T${next.season}:E${next.episodeNum}")
                    delay(2_500)
                    if (_playable.value == p) startPlayable(p.copy(episode = next))
                }
            }
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
                is Playable.Movie -> Unit
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playerManager.player.seekTo(positionMs)
        viewModelScope.launch { _playable.value?.let { savePosition(it) } }
    }

    override fun onCleared() {
        // viewModelScope ya está cancelado aquí; runBlocking sí llega a Room.
        val current = _playable.value
        playerManager.onEnded = null
        if (current != null) {
            kotlinx.coroutines.runBlocking { savePosition(current) }
        }
        playerManager.stop()
        super.onCleared()
    }

    companion object {
        private val DIACRITICS = Regex("\\p{Mn}+")

        fun formatMs(ms: Long): String {
            val totalSec = ms / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
