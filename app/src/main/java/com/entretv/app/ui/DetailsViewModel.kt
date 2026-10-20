package com.entretv.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entretv.app.data.download.DownloadRepository
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.FavoriteEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.SeriesEntity
import com.entretv.app.data.local.VodMovieEntity
import com.entretv.app.data.remote.ContentDetails
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.navigation.DetailsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Botón principal de una serie: qué episodio abre y con qué texto. */
data class ResumeTarget(
    val episode: EpisodeEntity,
    val label: String,
    val progress: Float,
    val isResume: Boolean
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository
) : ViewModel() {

    private val route: DetailsRoute = savedStateHandle.toRoute()
    val contentKey: String = route.contentKey
    val isSeries: Boolean = route.kind == "SERIES"

    private val _movie = MutableStateFlow<VodMovieEntity?>(null)
    val movie: StateFlow<VodMovieEntity?> = _movie

    private val _series = MutableStateFlow<SeriesEntity?>(null)
    val series: StateFlow<SeriesEntity?> = _series

    private val _details = MutableStateFlow(repository.cachedDetails(contentKey))
    val details: StateFlow<ContentDetails?> = _details

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val _selectedSeason = MutableStateFlow<Int?>(null)
    val selectedSeason: StateFlow<Int?> = _selectedSeason

    val isFavorite: StateFlow<Boolean> = repository.isFavorite(contentKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val movieProgress: StateFlow<PlaybackProgressEntity?> =
        (if (isSeries) flowOf(null) else repository.observeProgress(contentKey))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val episodes: StateFlow<List<EpisodeEntity>> =
        (if (isSeries) repository.episodes(contentKey) else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val seriesProgressList =
        if (isSeries) repository.observeSeriesProgress(contentKey) else flowOf(emptyList())

    val episodeProgress: StateFlow<Map<String, PlaybackProgressEntity>> =
        seriesProgressList.map { list -> list.associateBy { it.contentKey } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** El último episodio visto; si ya lo terminó, el siguiente; si no vio nada, el primero. */
    val resumeTarget: StateFlow<ResumeTarget?> =
        combine(episodes, seriesProgressList) { eps, progress ->
            if (eps.isEmpty()) return@combine null
            val byId = eps.withIndex().associateBy { it.value.id }
            val last = progress.firstOrNull { it.contentKey in byId }
            val lastIndexed = last?.let { byId[it.contentKey] }
            when {
                last == null || lastIndexed == null ->
                    ResumeTarget(eps.first(), "Reproducir ${eps.first().code()}", 0f, false)
                last.isCompleted -> {
                    val next = eps.getOrNull(lastIndexed.index + 1)
                    if (next != null) ResumeTarget(next, "Siguiente: ${next.code()}", 0f, false)
                    else ResumeTarget(eps.first(), "Volver a ver ${eps.first().code()}", 0f, false)
                }
                else -> ResumeTarget(
                    lastIndexed.value,
                    "Continuar ${lastIndexed.value.code()}",
                    if (last.durationMs > 0) last.positionMs.toFloat() / last.durationMs else 0f,
                    true
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val clean = settings.catalogPrefs.first().cleanNames
        fun tidy(raw: String) = TitleCleaner.clean(raw, clean)
        if (isSeries) {
            val s = repository.seriesById(contentKey)
            _series.value = s?.copy(name = tidy(s.name), categoryName = tidy(s.categoryName))
            if (s != null) {
                repository.seriesDetails(s)
                    .onSuccess { _details.value = it }
                    .onFailure { if (_details.value == null) _details.value = ContentDetails(plot = s.plot) }
            }
        } else {
            val m = repository.movieById(contentKey)
            _movie.value = m?.copy(name = tidy(m.name), categoryName = tidy(m.categoryName))
            if (m != null) {
                repository.movieDetails(m)
                    .onSuccess { _details.value = it }
                    .onFailure { if (_details.value == null) _details.value = ContentDetails() }
            }
        }
        _loading.value = false
    }

    fun selectSeason(season: Int) { _selectedSeason.value = season }

    fun toggleFavorite() {
        val favorite = _movie.value?.let {
            FavoriteEntity(it.id, "MOVIE", it.playlistId, it.name, it.icon)
        } ?: _series.value?.let {
            FavoriteEntity(it.id, "SERIES", it.playlistId, it.name, it.cover)
        } ?: return
        viewModelScope.launch {
            val added = repository.toggleFavorite(favorite)
            _notice.value = if (added) "Agregado a Mi lista" else "Quitado de Mi lista"
        }
    }

    fun consumeNotice() { _notice.value = null }

    // ============================ Descargas ============================

    val movieDownload: StateFlow<DownloadEntity?> =
        (if (isSeries) flowOf(null) else downloads.observe(contentKey))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val episodeDownloads: StateFlow<Map<String, DownloadEntity>> =
        (if (isSeries) downloads.observeSeries(contentKey) else flowOf(emptyList()))
            .map { list -> list.associateBy { it.contentKey } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun downloadMovie() {
        val m = _movie.value ?: return
        viewModelScope.launch {
            downloads.downloadMovie(m, m.name)
            _notice.value = downloadNotice()
        }
    }

    fun downloadEpisode(episode: EpisodeEntity) = downloadEpisodes(listOf(episode))

    fun downloadSeason(season: Int) {
        val pending = episodes.value.filter {
            it.season == season && episodeDownloads.value[it.id]?.isDone != true
        }
        if (pending.isEmpty()) {
            _notice.value = "Esta temporada ya está descargada"
            return
        }
        downloadEpisodes(pending)
    }

    private fun downloadEpisodes(list: List<EpisodeEntity>) {
        val s = _series.value ?: return
        viewModelScope.launch {
            downloads.downloadEpisodes(list, s.name, s.cover)
            _notice.value = if (list.size > 1) "${list.size} episodios en cola. ${downloadNotice()}" else downloadNotice()
        }
    }

    private suspend fun downloadNotice(): String =
        if (settings.downloadWifiOnly.first()) "Se descargará con Wi‑Fi; puedes verla en Descargas."
        else "Descargando; puedes verla en Descargas."

    fun pauseDownload(key: String) { viewModelScope.launch { downloads.pause(key) } }

    fun resumeDownload(key: String) { viewModelScope.launch { downloads.resume(key) } }

    fun deleteDownload(key: String) {
        viewModelScope.launch {
            downloads.delete(key)
            _notice.value = "Descarga eliminada"
        }
    }

    /** Borra el minuto guardado y luego abre el reproductor desde 0. */
    fun playFromStart(key: String, open: () -> Unit) {
        viewModelScope.launch {
            repository.removeProgress(key)
            open()
        }
    }

    fun resetSeries() {
        viewModelScope.launch {
            repository.resetSeries(contentKey)
            _notice.value = "La serie volverá a empezar desde el episodio 1"
        }
    }
}

fun EpisodeEntity.code(): String = "T$season:E$episodeNum"
