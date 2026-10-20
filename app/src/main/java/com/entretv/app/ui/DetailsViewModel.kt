package com.entretv.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entretv.app.data.download.DownloadRepository
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.DownloadStatus
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.FavoriteEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.SeriesEntity
import com.entretv.app.data.local.VodMovieEntity
import com.entretv.app.data.remote.ContentDetails
import com.entretv.app.data.repo.MetadataRepository
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.repo.SeriesResume
import com.entretv.app.data.repo.SeriesResumeRule
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
    private val downloads: DownloadRepository,
    private val metadata: MetadataRepository
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

    /**
     * Botón principal de la serie, con la misma regla que "Seguir viendo": el episodio más
     * avanzado visto más de 3 minutos, o el siguiente si ya lo terminó.
     */
    val resumeTarget: StateFlow<ResumeTarget?> =
        combine(episodes, seriesProgressList) { eps, progress ->
            if (eps.isEmpty()) return@combine null
            val resume = SeriesResumeRule.resolve(eps, progress)
            val episode = resume.episode ?: eps.first()
            when (resume.mode) {
                SeriesResume.Mode.RESUME -> ResumeTarget(episode, "Continuar ${episode.code()}", resume.fraction, true)
                SeriesResume.Mode.NEXT -> ResumeTarget(episode, "Siguiente: ${episode.code()}", 0f, false)
                SeriesResume.Mode.REWATCH -> ResumeTarget(episode, "Volver a ver ${episode.code()}", 0f, false)
                SeriesResume.Mode.START -> ResumeTarget(episode, "Reproducir ${episode.code()}", 0f, false)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { load() }
    }

    /**
     * Primero lo guardado (sirve sin conexión), después el servidor y por último TMDB.
     * Cada paso que falla deja lo que ya se mostraba.
     */
    private suspend fun load() {
        val clean = settings.catalogPrefs.first().cleanNames
        fun tidy(raw: String) = TitleCleaner.clean(raw, clean)
        val saved = metadata.cachedDetails(contentKey)
        if (_details.value == null && saved != null) _details.value = saved
        if (isSeries) {
            val s = repository.seriesById(contentKey)
            _series.value = s?.copy(name = tidy(s.name), categoryName = tidy(s.categoryName))
            if (s != null) {
                val base = repository.seriesDetails(s).getOrNull()
                if (base == null && _details.value == null) _details.value = ContentDetails(plot = s.plot)
                _loading.value = false
                enrich(base, tidy(s.name), s.year)
            }
        } else {
            val m = repository.movieById(contentKey)
            _movie.value = m?.copy(name = tidy(m.name), categoryName = tidy(m.categoryName))
            if (m != null) {
                val base = repository.movieDetails(m).getOrNull()
                if (base == null && _details.value == null) _details.value = ContentDetails()
                _loading.value = false
                enrich(base, tidy(m.name), m.year)
            }
        }
        _loading.value = false
    }

    private suspend fun enrich(base: ContentDetails?, title: String, year: Int) {
        if (base != null) _details.value = mergeSaved(base, _details.value)
        val start = base ?: _details.value ?: return
        val full = metadata.enrich(contentKey, start, isSeries, title, year.takeIf { it > 0 } ?: start.year.toIntOrNull() ?: 0)
        _details.value = full
        repository.putDetails(contentKey, full)
        if (base != null || full !== start) metadata.saveDetails(contentKey, full)
    }

    /** Lo del servidor manda, pero sin perder lo que ya se había completado con TMDB. */
    private fun mergeSaved(server: ContentDetails, saved: ContentDetails?): ContentDetails {
        if (saved == null || saved.tmdbId.isEmpty()) return server
        return saved.copy(
            plot = server.plot.ifEmpty { saved.plot },
            genre = server.genre.ifEmpty { saved.genre },
            year = server.year.ifEmpty { saved.year },
            rating = server.rating.ifEmpty { saved.rating },
            durationLabel = server.durationLabel.ifEmpty { saved.durationLabel },
            cast = server.cast.ifEmpty { saved.cast },
            director = server.director.ifEmpty { saved.director },
            backdropUrl = server.backdropUrl.ifEmpty { saved.backdropUrl },
            posterUrl = server.posterUrl.ifEmpty { saved.posterUrl },
            trailerUrl = server.trailerUrl.ifEmpty { saved.trailerUrl }
        )
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

    /** Episodios de esta serie en cola, bajándose o en pausa. */
    val pendingSeriesDownloads: StateFlow<Int> = episodeDownloads
        .map { map ->
            map.values.count {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PAUSED
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun stopSeriesDownloads() {
        viewModelScope.launch {
            val cancelled = downloads.cancelSeries(contentKey)
            _notice.value = when (cancelled) {
                0 -> "No había descargas pendientes"
                1 -> "Se canceló 1 descarga. Lo ya descargado se conserva."
                else -> "Se cancelaron $cancelled descargas. Lo ya descargado se conserva."
            }
        }
    }

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
