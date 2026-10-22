package com.entretv.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entretv.app.R
import com.entretv.app.data.backgroundExecutor
import com.entretv.app.data.download.DownloadRepository
import com.entretv.app.data.local.CategoryPrefEntity
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.FavoriteEntity
import com.entretv.app.data.local.HiddenItemEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.PlaylistEntity
import com.entretv.app.data.local.SeriesEntity
import com.entretv.app.data.local.VodMovieEntity
import com.entretv.app.data.repo.AccountException
import com.entretv.app.data.repo.MetadataRepository
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.repo.SeriesResume
import com.entretv.app.data.repo.SeriesResumeRule
import com.entretv.app.data.repo.TrendingTitle
import com.entretv.app.data.settings.AccountInfo
import com.entretv.app.data.settings.CatalogPrefs
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.PlayerPrefs
import com.entretv.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Elemento navegable del catálogo estilo Netflix. [title] llega ya limpio si el ajuste está activo. */
sealed interface BrowseItem {
    val key: String
    val title: String
    val imageUrl: String
    /** Fecha Unix en que el servidor lo agregó; 0 si no informa. */
    val added: Long get() = 0
    /** Puntuación 0–10; 0 si no informa. */
    val rating: Float get() = 0f

    data class Live(val channel: ChannelEntity, override val title: String = channel.name) : BrowseItem {
        override val key get() = channel.id
        override val imageUrl get() = channel.logoUrl
    }

    data class Movie(val movie: VodMovieEntity, override val title: String = movie.name) : BrowseItem {
        override val key get() = movie.id
        override val imageUrl get() = movie.icon
        override val added get() = movie.added
        override val rating get() = movie.rating
    }

    data class Show(val series: SeriesEntity, override val title: String = series.name) : BrowseItem {
        override val key get() = series.id
        override val imageUrl get() = series.cover
        override val added get() = series.added
        override val rating get() = series.rating
    }
}

/** Una fila horizontal estilo Netflix. [title] es la categoría tal cual; [label] lo que se muestra. */
@Immutable
data class ContentRow(val title: String, val items: List<BrowseItem>, val label: UiText = UiText.Raw(title))

enum class Section(@StringRes val labelRes: Int) {
    LIVE(R.string.section_live), MOVIES(R.string.section_movies), SERIES(R.string.section_series)
}

val Section.label: String
    @Composable get() = stringResource(labelRes)

/** Orden del catálogo. [ORIGINAL] respeta el orden en que lo manda el servidor. */
enum class SortOrder(@StringRes val labelRes: Int) {
    ORIGINAL(R.string.sort_original), AZ(R.string.sort_az), RATING(R.string.sort_rating), RECENT(R.string.sort_recent);

    companion object {
        fun fromName(name: String): SortOrder = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

val SortOrder.label: String
    @Composable get() = stringResource(labelRes)

/** Un hilo de prioridad baja para preparar el catálogo: el video conserva la CPU. */
private val warmDispatcher by lazy { backgroundExecutor("catalog-warm", 1).asCoroutineDispatcher() }

const val MY_LIST_ROW = "Mi lista"
/** "Todas": todo el contenido de la sección en cuadrícula (null es el inicio de la sección). */
const val ALL_ROW = "__all__"
const val RECENT_ROW = "Recién agregados"
const val TOP_RATED_ROW = "Mejor valorados"

/** Grupo de lo que llega del servidor sin categoría. */
private const val LIVE_GROUP = "En vivo"
private const val MOVIES_GROUP = "Películas"
private const val SERIES_GROUP = "Series"
private val FALLBACK_GROUPS = setOf(LIVE_GROUP, MOVIES_GROUP, SERIES_GROUP)

/** Texto de una fila especial en el idioma de la app; las categorías se muestran tal cual. */
fun rowLabel(row: String): UiText = when (row) {
    MY_LIST_ROW -> uiText(R.string.home_my_list)
    ALL_ROW -> uiText(R.string.home_all)
    RECENT_ROW -> uiText(R.string.home_row_recent)
    TOP_RATED_ROW -> uiText(R.string.home_row_top_rated)
    LIVE_GROUP -> uiText(R.string.section_live)
    MOVIES_GROUP -> uiText(R.string.section_movies)
    SERIES_GROUP -> uiText(R.string.section_series)
    else -> UiText.Raw(row)
}

/** Filas del resumen del celular. */
const val SUMMARY_MOVIES = "summary:movies"
const val SUMMARY_SERIES = "summary:series"
const val SUMMARY_EVENTS = "summary:events"
private const val SUMMARY_ROW_SIZE = 20
private const val EVENTS_ROW_SIZE = 40

/** Resumen al abrir la app en el celular: lo último agregado y los eventos de hoy. */
@Immutable
data class SummaryContent(
    val playlistId: String? = null,
    val rows: List<ContentRow> = emptyList(),
    /** Primera categoría de eventos: a donde lleva "Ver todo" de los eventos. */
    val eventsCategory: String? = null,
    val ready: Boolean = false
)

/** "Ver todo" de cada grupo de la búsqueda. */
const val SEARCH_MOVIES = "search:movies"
const val SEARCH_SERIES = "search:series"
const val SEARCH_LIVE = "search:live"

/** Una categoría en el diálogo "Organizar categorías". */
data class CategoryInfo(
    val name: String,
    val label: String,
    val hidden: Boolean,
    val adult: Boolean,
    /** Marcada como adultos a mano (no por el nombre). */
    val adultManual: Boolean?
)

/**
 * Lo que muestra la sección activa. Con [grid] las filas se pintan como bloques de
 * cuadrícula completos (categoría elegida con "Ver todo").
 */
@Immutable
data class SectionContent(
    val categories: List<String> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val categoryLabels: Map<String, String> = emptyMap(),
    /** Categorías de adultos bloqueadas por el PIN: piden el PIN al elegirlas. */
    val lockedCategories: Set<String> = emptySet(),
    val rows: List<ContentRow> = emptyList(),
    val grid: Boolean = false,
    /** Top 10 con números grandes; vacío en En vivo. */
    val top10: List<BrowseItem> = emptyList(),
    val top10Label: UiText = UiText.Raw(""),
    val myListCount: Int = 0,
    /** Resultados de búsqueda agrupados por tipo. */
    val isSearch: Boolean = false,
    /** Órdenes que tienen sentido para lo que se ve (en vivo no trae fecha ni puntuación). */
    val sortOptions: List<SortOrder> = emptyList(),
    /** Lista, sección y categoría para las que se calculó; null mientras no hay nada calculado. */
    val playlistId: String? = null,
    val section: Section? = null,
    val category: String? = null
)

/** Tarjeta de "Seguir viendo": una por película o por serie. */
@Immutable
data class ContinueItem(
    /** Película o serie (playlistId:id). */
    val key: String,
    val title: String,
    val subtitle: UiText,
    /** De la mejor a la de respaldo: guardada, del catálogo y de TMDB. */
    val images: List<String>,
    val fraction: Float,
    val remainingMs: Long,
    val isSeries: Boolean,
    /** Película o episodio que se abre; null = se resuelve al tocar (episodios aún no guardados). */
    val playKey: String?,
    val updatedAt: Long
)

@Immutable
data class HomeUiState(
    val playlists: List<PlaylistEntity> = emptyList(),
    val selectedPlaylistId: String? = null,
    val query: String = "",
    /** Primera descarga del catálogo (todavía no hay nada guardado). */
    val firstSync: Boolean = false,
    /** Actualización pedida a mano: muestra el indicador. */
    val refreshing: Boolean = false,
    val message: UiText? = null,
    val section: Section = Section.LIVE,
    /** null = "Todas". */
    val category: String? = null,
    val sort: SortOrder = SortOrder.ORIGINAL,
    /** Celular: se ve el resumen en lugar de una sección. */
    val summary: Boolean = true
)

private data class CatalogFilter(
    val playlistId: String?,
    val section: Section,
    val category: String?,
    val query: String
)

/** Datos crudos de la base para la vista activa. */
private data class RawCatalog(
    val filter: CatalogFilter,
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<VodMovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val favoriteKeys: List<String> = emptyList()
)

/** Vista ya armada y con qué datos se armó; las listas se comparan por identidad. */
private class BuiltContent(
    val raw: RawCatalog,
    val options: CatalogOptions,
    val order: SortOrder,
    val trending: Trending,
    val content: SectionContent
) {
    fun matches(r: RawCatalog, o: CatalogOptions, order: SortOrder, t: Trending): Boolean =
        raw.channels === r.channels && raw.movies === r.movies && raw.series === r.series &&
            raw.favoriteKeys == r.favoriteKeys && this.order == order && trending === t &&
            (options === o || options == o)
}

/** Lo que el usuario decidió sobre el catálogo. */
private data class CatalogOptions(
    val playlistId: String? = null,
    /** sección → categoría → posición en el servidor. */
    val serverOrder: Map<String, Map<String, Int>> = emptyMap(),
    val prefs: Map<Pair<String, String>, CategoryPrefEntity> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val cleanNames: Boolean = true,
    val locked: Boolean = false
)

private data class Trending(val movies: List<TrendingTitle> = emptyList(), val series: List<TrendingTitle> = emptyList())

private data class ContinueInput(
    val progress: List<PlaybackProgressEntity>,
    val section: Section,
    val excluded: Set<String>,
    val dismissed: Map<String, Long>,
    val cleanNames: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: PlaylistRepository,
    private val settings: SettingsRepository,
    private val parental: ParentalControl,
    private val downloads: DownloadRepository,
    private val metadata: MetadataRepository
) : ViewModel() {
    private val lowRam = isLowRamDevice(context)

    val downloadWifiOnly: StateFlow<Boolean> = settings.downloadWifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setDownloadWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settings.setDownloadWifiOnly(enabled)
            downloads.onNetworkPreferenceChanged()
        }
    }

    val bufferSeconds: StateFlow<Int> = settings.bufferSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_BUFFER_SECONDS)

    val maxBufferSeconds: StateFlow<Int> = settings.maxBufferSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_MAX_BUFFER_SECONDS)

    val catalogPrefs: StateFlow<CatalogPrefs> = settings.catalogPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogPrefs())

    val pinSet: StateFlow<Boolean> = parental.pinSet
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val parentalUnlocked: StateFlow<Boolean> = parental.unlocked

    val hiddenItems: StateFlow<List<HiddenItemEntity>> = repository.hiddenItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setBufferSeconds(seconds: Int) {
        viewModelScope.launch { settings.setBufferSeconds(seconds) }
    }

    fun setMaxBufferSeconds(seconds: Int) {
        viewModelScope.launch { settings.setMaxBufferSeconds(seconds) }
    }

    fun setDefaultSort(order: SortOrder) {
        sort.value = null
        viewModelScope.launch { settings.setDefaultSort(order.name) }
    }

    fun setCleanNames(enabled: Boolean) {
        viewModelScope.launch { settings.setCleanNames(enabled) }
    }

    val playerPrefs: StateFlow<PlayerPrefs> = settings.playerPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerPrefs())

    val showClock: StateFlow<Boolean> = settings.showClock
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setContentLanguage(language: String) {
        viewModelScope.launch { settings.setContentLanguage(language) }
    }

    fun setSubtitlesByDefault(enabled: Boolean) {
        viewModelScope.launch { settings.setSubtitlesByDefault(enabled) }
    }

    fun setLiveFormat(format: String) {
        viewModelScope.launch { settings.setLiveFormat(format) }
    }

    fun setDecoder(mode: Int) {
        viewModelScope.launch { settings.setDecoder(mode) }
    }

    fun setFrameRateMode(mode: Int) {
        viewModelScope.launch { settings.setFrameRateMode(mode) }
    }

    fun setTunneling(enabled: Boolean) {
        viewModelScope.launch { settings.setTunneling(enabled) }
    }

    fun setShowClock(enabled: Boolean) {
        viewModelScope.launch { settings.setShowClock(enabled) }
    }

    /** Secciones cuyas categorías se ven en lista en lugar de cuadrícula. */
    val listViewSections: StateFlow<Set<String>> = settings.listViewSections
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun setListView(section: Section, list: Boolean) {
        viewModelScope.launch { settings.setListView(section.name, list) }
    }

    private val selectedPlaylistId = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val firstSync = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<UiText?>(null)
    private val section = MutableStateFlow(Section.LIVE)
    private val category = MutableStateFlow<String?>(null)
    /** Orden elegido en esta sesión; null = el de Configuración. */
    private val sort = MutableStateFlow<SortOrder?>(null)
    private val trending = MutableStateFlow(Trending())
    private val summary = MutableStateFlow(true)

    private val effectiveSort: Flow<SortOrder> =
        combine(sort, settings.catalogPrefs) { s, p -> s ?: SortOrder.fromName(p.defaultSort) }
            .distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.playlists, selectedPlaylistId, query, firstSync,
        combine(refreshing, repository.syncing) { manual, any -> manual || any },
        message, section, category, effectiveSort, summary
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        HomeUiState(
            playlists = arr[0] as List<PlaylistEntity>,
            selectedPlaylistId = arr[1] as String?,
            query = arr[2] as String,
            firstSync = arr[3] as Boolean,
            refreshing = arr[4] as Boolean,
            message = arr[5] as UiText?,
            section = arr[6] as Section,
            category = arr[7] as String?,
            sort = arr[8] as SortOrder,
            summary = arr[9] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Cuenta activa (para Configuración › Cuenta). */
    val account: StateFlow<PlaylistEntity?> = combine(repository.playlists, selectedPlaylistId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Estado y vencimiento de la cuenta mostrada en Configuración. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val accountInfo: StateFlow<AccountInfo?> = account
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.accountInfo(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cuándo se actualizó por última vez el catálogo de la cuenta activa (0 = nunca). */
    val lastRefreshAt: StateFlow<Long> = account
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(0L) else settings.lastRefreshFlow(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val refreshMode: StateFlow<Int> = settings.refreshMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.REFRESH_DAILY)

    fun setRefreshMode(mode: Int) {
        viewModelScope.launch { settings.setRefreshMode(mode) }
    }

    private val hiddenKeys: Flow<Set<String>> =
        repository.hiddenItems.map { list -> list.map { it.contentKey }.toSet() }

    private val options: StateFlow<CatalogOptions> = selectedPlaylistId
        .flatMapLatest { pid ->
            if (pid == null) flowOf(CatalogOptions())
            else combine(
                repository.categories(pid), repository.categoryPrefs(pid), hiddenKeys,
                settings.catalogPrefs, parental.locked
            ) { cats, prefs, hidden, catalog, locked ->
                CatalogOptions(
                    playlistId = pid,
                    serverOrder = cats.groupBy { it.section }
                        .mapValues { (_, list) -> list.associate { it.name to it.position } },
                    prefs = prefs.associateBy { it.section to it.name },
                    hidden = hidden,
                    cleanNames = catalog.cleanNames,
                    locked = locked
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogOptions())

    /** Películas y series de categorías de adultos; solo se calcula con el PIN activo. */
    private val adultContentKeys: Flow<Set<String>> =
        combine(selectedPlaylistId, parental.locked) { pid, locked -> pid.takeIf { locked } }
            .distinctUntilChanged()
            .flatMapLatest { pid ->
                if (pid == null) flowOf(emptySet())
                else combine(repository.categories(pid), repository.categoryPrefs(pid)) { cats, prefs ->
                    val byName = prefs.associateBy { it.section to it.name }
                    fun adultIn(sectionKey: String): List<String> {
                        val names = cats.filter { it.section == sectionKey }.map { it.name } +
                            prefs.filter { it.section == sectionKey && it.adult == true }.map { it.name }
                        return names.distinct().filter { ParentalControl.isAdult(it, byName[sectionKey to it]) }
                    }
                    adultIn(PlaylistRepository.SECTION_MOVIES) to adultIn(PlaylistRepository.SECTION_SERIES)
                }
                    .distinctUntilChanged()
                    .mapLatest { (movies, series) -> repository.contentKeysIn(pid, movies, series) }
            }

    /**
     * "Seguir viendo" de la sección activa: una tarjeta por película o serie. En series manda
     * [SeriesResumeRule] (el episodio más avanzado o el siguiente si ya lo terminó).
     */
    val continueWatching: StateFlow<List<ContinueItem>> =
        combine(
            repository.continueWatching, section,
            combine(hiddenKeys, adultContentKeys) { a, b -> a + b },
            settings.continueDismissed, settings.catalogPrefs
        ) { list, s, excluded, dismissed, prefs -> ContinueInput(list, s, excluded, dismissed, prefs.cleanNames) }
            .mapLatest(::buildContinue)
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun buildContinue(input: ContinueInput): List<ContinueItem> {
        val visible = input.progress.filter { it.contentKey.substringBefore('#') !in input.excluded }
        return when (input.section) {
            Section.LIVE -> emptyList()
            Section.MOVIES -> visible.filter { it.kind == "MOVIE" && !it.isCompleted }.mapNotNull { p ->
                val movie = repository.movieById(p.contentKey) ?: return@mapNotNull null
                val cached = metadata.cachedDetails(movie.id)
                ContinueItem(
                    key = movie.id,
                    title = clean(movie.name, input.cleanNames),
                    subtitle = UiText.Raw(""),
                    images = listOf(p.imageUrl, movie.icon, cached?.backdropUrl.orEmpty(), cached?.posterUrl.orEmpty())
                        .filter { it.isNotBlank() }.distinct(),
                    fraction = p.fraction,
                    remainingMs = (p.durationMs - p.positionMs).coerceAtLeast(0),
                    isSeries = false,
                    playKey = movie.id,
                    updatedAt = p.updatedAt
                )
            }
            Section.SERIES -> visible.filter { it.kind == "EPISODE" }
                .groupBy { it.contentKey.substringBefore('#') }
                .mapNotNull { (seriesKey, progress) ->
                    val last = progress.maxOf { it.updatedAt }
                    if ((input.dismissed[seriesKey] ?: 0L) >= last) return@mapNotNull null
                    val series = repository.seriesById(seriesKey) ?: return@mapNotNull null
                    val resume = SeriesResumeRule.resolve(repository.episodeSnapshot(seriesKey), progress)
                    val anchor = resume.anchor ?: return@mapNotNull null
                    val target = resume.episode
                    val cached = metadata.cachedDetails(seriesKey)
                    val (subtitle, fraction, remaining) = when (resume.mode) {
                        SeriesResume.Mode.RESUME -> Triple(
                            UiText.Raw(
                                anchor.subtitle.substringBefore(" · ").ifBlank { target?.let { "T${it.season}:E${it.episodeNum}" }.orEmpty() }
                            ),
                            anchor.fraction,
                            (anchor.durationMs - anchor.positionMs).coerceAtLeast(0)
                        )
                        SeriesResume.Mode.NEXT -> Triple(
                            target?.let { uiText(R.string.home_continue_next, "T${it.season}:E${it.episodeNum}") }
                                ?: uiText(R.string.home_continue_next_episode),
                            0f, 0L
                        )
                        // Terminó la serie entera: ya no hay nada que seguir.
                        else -> return@mapNotNull null
                    }
                    ContinueItem(
                        key = seriesKey,
                        title = clean(series.name, input.cleanNames),
                        subtitle = subtitle,
                        images = listOf(
                            if (resume.mode == SeriesResume.Mode.RESUME) anchor.imageUrl else "",
                            target?.imageUrl.orEmpty(), series.cover, anchor.imageUrl,
                            cached?.backdropUrl.orEmpty(), cached?.posterUrl.orEmpty()
                        ).filter { it.isNotBlank() }.distinct(),
                        fraction = fraction,
                        remainingMs = remaining,
                        isSeries = true,
                        playKey = if (resume.mode == SeriesResume.Mode.RESUME) anchor.contentKey else target?.id,
                        updatedAt = last
                    )
                }
                .sortedByDescending { it.updatedAt }
        }
    }

    private val favoriteKeys = repository.favorites.map { list -> list.map { it.contentKey } }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    // Cada tabla se lee una vez y queda en memoria: cambiar de sección o de categoría no vuelve a
    // consultar la base; Room manda la lista nueva solo cuando el catálogo cambia.
    private fun <T> catalogTable(source: (String) -> Flow<List<T>>): Flow<Pair<String, List<T>>> =
        selectedPlaylistId.filterNotNull()
            .flatMapLatest { pid -> source(pid).map { pid to it } }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val channelTable = catalogTable(repository::channels)
    private val movieTable = catalogTable(repository::movies)
    private val seriesTable = catalogTable(repository::series)

    private fun <T> Flow<Pair<String, List<T>>>.of(pid: String): Flow<List<T>> =
        filter { it.first == pid }.map { it.second }

    // Cada entrada de "Todas" o de una búsqueda envuelve miles de títulos.
    private val builtCache = object : LinkedHashMap<CatalogFilter, BuiltContent>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CatalogFilter, BuiltContent>?) =
            size > if (lowRam) 4 else 12
    }

    val favoriteKeySet: StateFlow<Set<String>> = favoriteKeys.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Solo se observa la tabla de la sección activa. La búsqueda cruza canales, películas y series.
     */
    val content: StateFlow<SectionContent> =
        combine(selectedPlaylistId, section, category, query.debounce(250)) { pid, s, c, q ->
            CatalogFilter(pid, s, c, q.trim())
        }
            .flatMapLatest { f ->
                val pid = f.playlistId ?: return@flatMapLatest flowOf(RawCatalog(f))
                when {
                    f.query.isNotEmpty() -> combine(
                        channelTable.of(pid), movieTable.of(pid), seriesTable.of(pid)
                    ) { channels, movies, series -> RawCatalog(f, channels, movies, series) }

                    f.section == Section.LIVE -> combine(channelTable.of(pid), favoriteKeys) { list, keys ->
                        RawCatalog(f, channels = list, favoriteKeys = keys)
                    }
                    f.section == Section.MOVIES -> combine(movieTable.of(pid), favoriteKeys) { list, keys ->
                        RawCatalog(f, movies = list, favoriteKeys = keys)
                    }
                    else -> combine(seriesTable.of(pid), favoriteKeys) { list, keys ->
                        RawCatalog(f, series = list, favoriteKeys = keys)
                    }
                }
            }
            .let { raw ->
                combine(raw, options, effectiveSort, trending) { r, o, order, t ->
                    val cached = synchronized(builtCache) { builtCache[r.filter] }?.takeIf { it.matches(r, o, order, t) }
                    cached?.content ?: build(r, o, order, t).copy(
                        playlistId = r.filter.playlistId,
                        section = r.filter.section,
                        category = r.filter.category
                    ).also { built ->
                        synchronized(builtCache) { builtCache[r.filter] = BuiltContent(r, o, order, t, built) }
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionContent())

    private fun build(r: RawCatalog, o: CatalogOptions, order: SortOrder, t: Trending): SectionContent {
        val f = r.filter
        if (f.playlistId == null) return SectionContent()
        val c = o.cleanNames
        return when {
            f.query.isNotEmpty() -> searchAll(r, o, order)
            f.section == Section.LIVE -> buildContent(
                r.channels, PlaylistRepository.SECTION_LIVE, f, r.favoriteKeys, o, order, trending = null,
                ::liveGroup, { it.id }
            ) { BrowseItem.Live(it, clean(it.name, c)) }
            f.section == Section.MOVIES -> buildContent(
                r.movies, PlaylistRepository.SECTION_MOVIES, f, r.favoriteKeys, o, order, trending = t.movies,
                ::movieGroup, { it.id }
            ) { BrowseItem.Movie(it, clean(it.name, c)) }
            else -> buildContent(
                r.series, PlaylistRepository.SECTION_SERIES, f, r.favoriteKeys, o, order, trending = t.series,
                ::seriesGroup, { it.id }
            ) { BrowseItem.Show(it, clean(it.name, c)) }
        }
    }

    private fun liveGroup(channel: ChannelEntity): String = channel.groupTitle.ifEmpty { LIVE_GROUP }
    private fun movieGroup(movie: VodMovieEntity): String = movie.categoryName.ifEmpty { MOVIES_GROUP }
    private fun seriesGroup(series: SeriesEntity): String = series.categoryName.ifEmpty { SERIES_GROUP }

    /** Lo que se puede mostrar de una sección: sin categorías ocultas o bloqueadas ni títulos ocultos. */
    private fun <T> visibleBySection(
        items: List<T>,
        sectionKey: String,
        o: CatalogOptions,
        groupOf: (T) -> String,
        keyOf: (T) -> String
    ): List<T> {
        val skip = excluder(sectionKey, o)
        return items.filter { keyOf(it) !in o.hidden && !skip(groupOf(it)) }
    }

    // ============================ Resumen (celular) ============================

    /** "Seguir viendo" del resumen: películas y series juntas, lo último primero. */
    val summaryContinue: StateFlow<List<ContinueItem>> =
        combine(
            repository.continueWatching,
            combine(hiddenKeys, adultContentKeys) { a, b -> a + b },
            settings.continueDismissed, settings.catalogPrefs
        ) { list, excluded, dismissed, prefs -> ContinueInput(list, Section.MOVIES, excluded, dismissed, prefs.cleanNames) }
            .mapLatest { input ->
                (buildContinue(input) + buildContinue(input.copy(section = Section.SERIES)))
                    .sortedByDescending { it.updatedAt }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Solo se calcula mientras el resumen está en pantalla. */
    val summaryContent: StateFlow<SummaryContent> = selectedPlaylistId
        .flatMapLatest { pid ->
            if (pid == null) flowOf(SummaryContent(ready = true))
            else combine(
                channelTable.of(pid), movieTable.of(pid), seriesTable.of(pid), options
            ) { channels, movies, series, o -> buildSummary(pid, channels, movies, series, o) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryContent())

    private fun buildSummary(
        pid: String,
        channels: List<ChannelEntity>,
        movies: List<VodMovieEntity>,
        series: List<SeriesEntity>,
        o: CatalogOptions
    ): SummaryContent {
        val c = o.cleanNames
        val newMovies = visibleBySection(movies, PlaylistRepository.SECTION_MOVIES, o, ::movieGroup) { it.id }
            .filter { it.added > 0 }
            .sortedByDescending { it.added }
            .take(SUMMARY_ROW_SIZE)
            .map { BrowseItem.Movie(it, clean(it.name, c)) }
        val newSeries = visibleBySection(series, PlaylistRepository.SECTION_SERIES, o, ::seriesGroup) { it.id }
            .filter { it.added > 0 }
            .sortedByDescending { it.added }
            .take(SUMMARY_ROW_SIZE)
            .map { BrowseItem.Show(it, clean(it.name, c)) }

        val isEvents = HashMap<String, Boolean>()
        val eventChannels = visibleBySection(channels, PlaylistRepository.SECTION_LIVE, o, ::liveGroup) { it.id }
            .filter { ch -> isEvents.getOrPut(liveGroup(ch)) { normalize(liveGroup(ch)).contains("evento") } }
        val eventCategories = isEvents.filterValues { it }.keys
        val firstEvents = orderedCategories(PlaylistRepository.SECTION_LIVE, eventCategories, o, SortOrder.ORIGINAL)
            .firstOrNull { it in eventCategories }

        val rows = listOf(
            ContentRow(SUMMARY_MOVIES, newMovies, uiText(R.string.home_summary_new_movies)),
            ContentRow(SUMMARY_SERIES, newSeries, uiText(R.string.home_summary_new_series)),
            ContentRow(
                SUMMARY_EVENTS,
                sortEvents(eventChannels).take(EVENTS_ROW_SIZE).map { BrowseItem.Live(it, clean(it.name, c)) },
                uiText(R.string.home_summary_events)
            )
        ).filter { it.items.isNotEmpty() }
        return SummaryContent(playlistId = pid, rows = rows, eventsCategory = firstEvents, ready = true)
    }

    private val eventTime = Regex("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?")

    /** Minuto del día de la hora escrita en el nombre ("20:30", "8:30 PM"); null si no trae. */
    private fun eventMinute(name: String): Int? {
        val match = eventTime.find(name) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (minute > 59) return null
        when (match.groupValues[3].lowercase()) {
            "am" -> { if (hour !in 1..12) return null; if (hour == 12) hour = 0 }
            "pm" -> { if (hour !in 1..12) return null; if (hour != 12) hour += 12 }
            else -> if (hour > 23) return null
        }
        return hour * 60 + minute
    }

    /**
     * Eventos por la hora del nombre: primero los que vienen y los que empezaron hace menos
     * de 3 horas, después los demás y al final los que no dicen hora.
     */
    private fun sortEvents(channels: List<ChannelEntity>): List<ChannelEntity> {
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        return channels.map { it to eventMinute(it.name) }
            .sortedWith(compareBy({ (_, minute) ->
                if (minute == null) 2 else if (dayOffset(minute - now) >= -180) 0 else 1
            }, { (_, minute) -> minute?.let { dayOffset(it - now) } ?: 0 }))
            .map { it.first }
    }

    /** Diferencia en minutos llevada a ±12 h: pasada la medianoche sigue siendo "hoy". */
    private fun dayOffset(diff: Int): Int = when {
        diff < -720 -> diff + 1440
        diff >= 720 -> diff - 1440
        else -> diff
    }

    fun showSummary() {
        if (query.value.isNotBlank()) onQueryChange("")
        category.value = null
        summary.value = true
    }

    /** Celular: entra a una sección (y opcionalmente a una categoría) desde el resumen o la barra. */
    fun openSection(s: Section, c: String? = null) {
        summary.value = false
        section.value = s
        category.value = c
    }

    private val cleanCache = ConcurrentHashMap<String, String>()

    private fun clean(raw: String, enabled: Boolean): String =
        if (!enabled) raw else cleanCache.getOrPut(raw) { TitleCleaner.clean(raw) }

    private fun sortItems(items: List<BrowseItem>, order: SortOrder): List<BrowseItem> = when (order) {
        SortOrder.ORIGINAL -> items
        SortOrder.RECENT -> items.sortedByDescending { it.added }
        SortOrder.AZ -> items.sortedBy { normalizedTitle(it.title) }
        SortOrder.RATING -> items.sortedByDescending { it.rating }
    }

    /** En vivo no hay fecha ni puntuación: esos órdenes caen en el original. */
    private fun orderFor(sectionKey: String, order: SortOrder): SortOrder =
        if (sectionKey == PlaylistRepository.SECTION_LIVE && (order == SortOrder.RATING || order == SortOrder.RECENT)) {
            SortOrder.ORIGINAL
        } else order

    /**
     * Categorías de la sección en orden: primero el que armó el usuario, después el del
     * servidor. Sin orden propio y con A-Z elegido, alfabético.
     */
    private fun orderedCategories(
        sectionKey: String,
        present: Collection<String>,
        o: CatalogOptions,
        order: SortOrder
    ): List<String> {
        val server = o.serverOrder[sectionKey].orEmpty()
        val names = (server.keys + present).distinct()
        val userOrdered = names.any { o.prefs[sectionKey to it]?.userPosition != null }
        if (!userOrdered && order == SortOrder.AZ) return names.sortedBy { normalize(clean(it, o.cleanNames)) }
        val appearance = present.withIndex().associate { it.value to it.index }
        return names.sortedWith(
            compareBy<String>(
                { o.prefs[sectionKey to it]?.userPosition ?: Int.MAX_VALUE },
                { server[it] ?: Int.MAX_VALUE },
                { appearance[it] ?: Int.MAX_VALUE }
            )
        )
    }

    /** Categoría que no se muestra: oculta, o de adultos con el PIN puesto. */
    private fun excluder(sectionKey: String, o: CatalogOptions): (String) -> Boolean {
        val memo = HashMap<String, Boolean>()
        return { name ->
            memo.getOrPut(name) {
                val pref = o.prefs[sectionKey to name]
                pref?.hidden == true || (o.locked && ParentalControl.isAdult(name, pref))
            }
        }
    }

    private fun <T> buildContent(
        items: List<T>,
        sectionKey: String,
        f: CatalogFilter,
        favoriteKeys: List<String>,
        o: CatalogOptions,
        requestedOrder: SortOrder,
        /** null = sección sin Top 10 (En vivo). */
        trending: List<TrendingTitle>?,
        groupOf: (T) -> String,
        keyOf: (T) -> String,
        wrap: (T) -> BrowseItem
    ): SectionContent {
        val order = orderFor(sectionKey, requestedOrder)
        val allGroups = items.groupBy(groupOf)
        val names = orderedCategories(sectionKey, allGroups.keys, o, order).filter { it in allGroups }
        val hiddenCats = names.filter { o.prefs[sectionKey to it]?.hidden == true }.toSet()
        val locked = if (o.locked) {
            names.filter { ParentalControl.isAdult(it, o.prefs[sectionKey to it]) }.toSet()
        } else emptySet()
        val shown = names.filter { it !in hiddenCats }
        val visibleRaw: Map<String, List<T>> = shown.filter { it !in locked }.associateWith { n ->
            val list = allGroups[n].orEmpty()
            if (o.hidden.isEmpty()) list else list.filter { keyOf(it) !in o.hidden }
        }
        val counts = shown.associateWith { n ->
            visibleRaw[n]?.size ?: allGroups[n].orEmpty().count { keyOf(it) !in o.hidden }
        }
        val labels = shown.filter { it !in FALLBACK_GROUPS }.associateWith { clean(it, o.cleanNames) }

        fun groupItems(name: String, limit: Int): List<BrowseItem> {
            val raw = visibleRaw[name].orEmpty()
            return if (order == SortOrder.ORIGINAL) raw.take(limit).map(wrap)
            else sortItems(raw.map(wrap), order).take(limit)
        }

        val wrapped by lazy { visibleRaw.values.flatten().map(wrap) }
        val recent by lazy { wrapped.filter { it.added > 0 }.sortedByDescending { it.added } }
        val topRated by lazy { wrapped.filter { it.rating > 0f }.sortedByDescending { it.rating } }
        val sortOptions = when {
            items.isEmpty() -> emptyList()
            sectionKey == PlaylistRepository.SECTION_LIVE -> listOf(SortOrder.ORIGINAL, SortOrder.AZ)
            else -> SortOrder.entries.toList()
        }
        val byKey = visibleRaw.values.flatten().associateBy(keyOf)
        val mine = favoriteKeys.mapNotNull { byKey[it] }.map(wrap)

        if (f.category != null) {
            val list = when (f.category) {
                ALL_ROW -> if (order == SortOrder.ORIGINAL) wrapped else sortItems(wrapped, order)
                MY_LIST_ROW -> if (order == SortOrder.ORIGINAL) mine else sortItems(mine, order)
                RECENT_ROW -> if (order == SortOrder.ORIGINAL) recent else sortItems(recent, order)
                TOP_RATED_ROW -> if (order == SortOrder.ORIGINAL) topRated else sortItems(topRated, order)
                else -> groupItems(f.category, Int.MAX_VALUE)
            }
            return SectionContent(
                categories = shown,
                categoryCounts = counts,
                categoryLabels = labels,
                lockedCategories = locked,
                rows = listOf(ContentRow(f.category, list, labels[f.category]?.let { UiText.Raw(it) } ?: rowLabel(f.category))),
                grid = true,
                myListCount = mine.size,
                sortOptions = sortOptions
            )
        }
        val top10 = if (trending != null && wrapped.isNotEmpty()) topTen(wrapped, trending, topRated + recent) else emptyList<BrowseItem>() to false
        val rows = buildList {
            // Solo si el servidor informa fechas o puntuaciones de verdad.
            if (trending != null && recent.size >= 5) add(ContentRow(RECENT_ROW, recent.take(20), rowLabel(RECENT_ROW)))
            if (trending != null && topRated.size >= 5) add(ContentRow(TOP_RATED_ROW, topRated.take(20), rowLabel(TOP_RATED_ROW)))
            visibleRaw.keys.forEach { group ->
                val list = groupItems(group, 20)
                if (list.isNotEmpty()) add(ContentRow(group, list, UiText.Raw(labels[group] ?: group)))
            }
        }
        val sectionName = uiText(if (sectionKey == PlaylistRepository.SECTION_SERIES) R.string.section_series else R.string.section_movies)
        return SectionContent(
            categories = shown,
            categoryCounts = counts,
            categoryLabels = labels,
            lockedCategories = locked,
            rows = listOfNotNull(mine.takeIf { it.isNotEmpty() }?.let { ContentRow(MY_LIST_ROW, it.take(30), rowLabel(MY_LIST_ROW)) }) + rows,
            top10 = top10.first.takeIf { it.size >= 3 }.orEmpty(),
            top10Label = if (top10.second) uiText(R.string.home_top10_today, sectionName) else uiText(R.string.home_top10_catalog, sectionName),
            myListCount = mine.size,
            sortOptions = sortOptions
        )
    }

    // ============================ Top 10 ============================

    private val matchCache = ConcurrentHashMap<String, String>()

    /** Clave para cruzar títulos: sin prefijos, año, acentos ni signos. */
    private fun matchKey(title: String): String = matchCache.getOrPut(title) {
        val n = normalize(withoutYearSuffix(clean(title, true)))
        buildString(n.length) { for (ch in n) if (ch in 'a'..'z' || ch in '0'..'9') append(ch) }
    }

    /** Quita "(2021)", "[2021]" o "- 2021" del final, sin el costo de una expresión regular. */
    private fun withoutYearSuffix(text: String): String {
        fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'
        var i = text.length - 1
        while (i >= 0 && isSpace(text[i])) i--
        if (i >= 0 && (text[i] == ')' || text[i] == ']')) i--
        while (i >= 0 && isSpace(text[i])) i--
        if (i < 3) return text
        val y = text.substring(i - 3, i + 1)
        if (!y.all { it in '0'..'9' } || !(y.startsWith("19") || y.startsWith("20"))) return text
        i -= 4
        while (i >= 0 && isSpace(text[i])) i--
        if (i < 0 || text[i] !in "([-") return text
        return text.substring(0, i)
    }

    /** Nombre tal como llega del servidor: su versión limpia ya quedó en caché al armar la sección. */
    private fun BrowseItem.rawName(): String = when (this) {
        is BrowseItem.Movie -> movie.name
        is BrowseItem.Show -> series.name
        is BrowseItem.Live -> channel.name
    }

    private fun BrowseItem.tmdbId(): String = when (this) {
        is BrowseItem.Movie -> movie.tmdbId
        is BrowseItem.Show -> series.tmdbId
        is BrowseItem.Live -> ""
    }

    private fun BrowseItem.year(): Int = when (this) {
        is BrowseItem.Movie -> movie.year
        is BrowseItem.Show -> series.year
        is BrowseItem.Live -> 0
    }

    /**
     * Top 10 solo con títulos que el usuario tiene: se cruzan las tendencias de TMDB con el
     * catálogo por id de TMDB o por título + año. Lo que falte se completa con [fill].
     * @return los 10 y si alguno vino de las tendencias.
     */
    private fun topTen(all: List<BrowseItem>, trending: List<TrendingTitle>, fill: List<BrowseItem>): Pair<List<BrowseItem>, Boolean> {
        val picked = LinkedHashMap<String, BrowseItem>()
        if (trending.isNotEmpty()) {
            val byTmdb = HashMap<String, BrowseItem>()
            val byTitle = HashMap<String, MutableList<BrowseItem>>()
            all.forEach { item ->
                item.tmdbId().takeIf { it.isNotEmpty() }?.let { byTmdb.putIfAbsent(it, item) }
                val key = matchKey(item.rawName())
                if (key.length >= 2) byTitle.getOrPut(key) { mutableListOf() }.add(item)
            }
            for (t in trending) {
                if (picked.size >= 10) break
                val hit = byTmdb[t.tmdbId] ?: listOf(t.title, t.originalTitle).filter { it.isNotBlank() }.firstNotNullOfOrNull { title ->
                    val candidates = byTitle[matchKey(title)] ?: return@firstNotNullOfOrNull null
                    candidates.firstOrNull { t.year > 0 && it.year() == t.year }
                        ?: candidates.firstOrNull { it.year() == 0 || t.year == 0 || abs(it.year() - t.year) <= 1 }
                }
                if (hit != null) picked.putIfAbsent(hit.key, hit)
            }
        }
        val matched = picked.isNotEmpty()
        for (item in fill) {
            if (picked.size >= 10) break
            picked.putIfAbsent(item.key, item)
        }
        if (picked.size < 10) {
            for (item in all) {
                if (picked.size >= 10) break
                if (item.imageUrl.isNotEmpty()) picked.putIfAbsent(item.key, item)
            }
        }
        return picked.values.toList() to matched
    }

    // ============================ Búsqueda ============================

    /** Nombres de una tabla ya normalizados, en el mismo orden; sirven mientras Room no mande otra lista. */
    private class SearchNames(val source: List<*>, val names: Array<String>)

    private val channelNames = AtomicReference<SearchNames?>()
    private val movieNames = AtomicReference<SearchNames?>()
    private val seriesNames = AtomicReference<SearchNames?>()

    /** Se normaliza cada nombre una vez por catálogo y no en cada tecla de la búsqueda. */
    private fun <T> searchNames(ref: AtomicReference<SearchNames?>, items: List<T>, name: (T) -> String): Array<String> {
        ref.get()?.takeIf { it.source === items }?.let { return it.names }
        val names = Array(items.size) { normalize(name(items[it])) }
        ref.set(SearchNames(items, names))
        return names
    }

    private fun <T> List<T>.matching(names: Array<String>, tokens: List<String>): List<T> =
        filterIndexed { i, _ -> val n = names[i]; tokens.all { n.contains(it) } }

    private fun searchAll(r: RawCatalog, o: CatalogOptions, order: SortOrder): SectionContent {
        val tokens = normalize(r.filter.query).split(' ').filter { it.isNotBlank() }
        val first = tokens.firstOrNull().orEmpty()
        // Con el orden original, primero los que empiezan con lo buscado.
        fun ranked(items: List<BrowseItem>, o2: SortOrder): List<BrowseItem> =
            if (o2 == SortOrder.ORIGINAL) items.sortedBy { if (normalizedTitle(it.title).startsWith(first)) 0 else 1 }
            else sortItems(items, o2)
        val c = o.cleanNames
        val movies = ranked(
            visibleBySection(
                r.movies.matching(searchNames(movieNames, r.movies) { it.name }, tokens),
                PlaylistRepository.SECTION_MOVIES, o, ::movieGroup
            ) { it.id }.map { BrowseItem.Movie(it, clean(it.name, c)) }, order
        )
        val series = ranked(
            visibleBySection(
                r.series.matching(searchNames(seriesNames, r.series) { it.name }, tokens),
                PlaylistRepository.SECTION_SERIES, o, ::seriesGroup
            ) { it.id }.map { BrowseItem.Show(it, clean(it.name, c)) }, order
        )
        val channels = ranked(
            visibleBySection(
                r.channels.matching(searchNames(channelNames, r.channels) { it.name }, tokens),
                PlaylistRepository.SECTION_LIVE, o, ::liveGroup
            ) { it.id }.map { BrowseItem.Live(it, clean(it.name, c)) }, orderFor(PlaylistRepository.SECTION_LIVE, order)
        )
        val groups = listOf(
            ContentRow(SEARCH_MOVIES, movies, uiText(R.string.home_search_movies_count, movies.size)),
            ContentRow(SEARCH_SERIES, series, uiText(R.string.home_search_series_count, series.size)),
            ContentRow(SEARCH_LIVE, channels, uiText(R.string.home_search_channels_count, channels.size))
        )
        val selected = groups.firstOrNull { it.title == r.filter.category }
        return if (selected != null) {
            SectionContent(rows = listOf(selected), grid = true, isSearch = true, sortOptions = SortOrder.entries.toList())
        } else {
            SectionContent(rows = groups.map { it.copy(items = it.items.take(30)) }, isSearch = true)
        }
    }

    private val accents = Regex("\\p{Mn}+")

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        if (lower.all { it.code < 128 }) return lower
        return accents.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
    }

    private val titleCache = ConcurrentHashMap<String, String>()

    /** Para ordenar títulos ya limpios (A-Z, búsqueda): se repiten en cada armado. */
    private fun normalizedTitle(title: String): String = titleCache.getOrPut(title) { normalize(title) }

    // ============================ Organizar categorías ============================

    private fun sectionKey(s: Section): String = when (s) {
        Section.LIVE -> PlaylistRepository.SECTION_LIVE
        Section.MOVIES -> PlaylistRepository.SECTION_MOVIES
        Section.SERIES -> PlaylistRepository.SECTION_SERIES
    }

    /** Sección que se está organizando en el diálogo. */
    private val organizeSection = MutableStateFlow(Section.LIVE)
    val organizingSection: StateFlow<Section> = organizeSection

    /** Todas las categorías de la sección (también las ocultas), en el orden en que se ven. */
    val organizeList: StateFlow<List<CategoryInfo>> =
        combine(options, organizeSection, effectiveSort, content) { o, s, order, _ -> managedCategories(s, o, order) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun organize(s: Section) {
        organizeSection.value = s
    }

    private fun managedCategories(s: Section, o: CatalogOptions, order: SortOrder): List<CategoryInfo> {
        val key = sectionKey(s)
        // Hasta la primera actualización completa la tabla puede estar vacía: se suman las que se ven.
        val visible = if (s == section.value && query.value.isBlank()) content.value.categories else emptyList()
        val remembered = if (o.serverOrder[key].isNullOrEmpty()) {
            o.prefs.keys.filter { it.first == key }.map { it.second }
        } else emptyList()
        val present = (visible + remembered).distinct()
        return orderedCategories(key, present, o, orderFor(key, order)).map { name ->
            val pref = o.prefs[key to name]
            CategoryInfo(
                name = name,
                label = clean(name, o.cleanNames),
                hidden = pref?.hidden == true,
                adult = ParentalControl.isAdult(name, pref),
                adultManual = pref?.adult
            )
        }
    }

    private fun prefFor(sectionKey: String, name: String): CategoryPrefEntity =
        options.value.prefs[sectionKey to name]
            ?: CategoryPrefEntity(options.value.playlistId.orEmpty(), sectionKey, name)

    private fun savePrefs(prefs: List<CategoryPrefEntity>) {
        if (prefs.isEmpty() || options.value.playlistId == null) return
        viewModelScope.launch { repository.saveCategoryPrefs(prefs) }
    }

    fun setCategoryHidden(s: Section, name: String, hidden: Boolean) {
        val key = sectionKey(s)
        savePrefs(listOf(prefFor(key, name).copy(hidden = hidden)))
        if (hidden && s == section.value && category.value == name) category.value = null
        if (hidden) message.value = uiText(R.string.home_category_hidden, clean(name, true))
    }

    fun setCategoryAdult(s: Section, name: String, adult: Boolean) {
        val key = sectionKey(s)
        savePrefs(listOf(prefFor(key, name).copy(adult = adult)))
        if (adult && s == section.value && category.value == name && pinSet.value && !parentalUnlocked.value) category.value = null
        message.value = when {
            adult && !pinSet.value -> uiText(R.string.home_marked_adult_no_pin)
            adult -> uiText(R.string.home_marked_adult, clean(name, true))
            else -> uiText(R.string.home_unmarked_adult, clean(name, true))
        }
    }

    /** Sube (-1) o baja (+1) una categoría; desde ahí el orden pasa a ser el del usuario. */
    fun moveCategory(s: Section, name: String, delta: Int) {
        val key = sectionKey(s)
        val names = managedCategories(s, options.value, uiState.value.sort).map { it.name }.toMutableList()
        val from = names.indexOf(name)
        val to = from + delta
        if (from < 0 || to !in names.indices) return
        names.add(to, names.removeAt(from))
        savePrefs(names.mapIndexed { index, n -> prefFor(key, n).copy(userPosition = index) })
    }

    fun resetCategoryOrder(s: Section) {
        val key = sectionKey(s)
        savePrefs(
            options.value.prefs.values
                .filter { it.section == key && it.userPosition != null }
                .map { it.copy(userPosition = null) }
        )
    }

    // ============================ Ocultar títulos ============================

    fun hideItem(item: BrowseItem) {
        val (kind, playlistId) = when (item) {
            is BrowseItem.Live -> "LIVE" to item.channel.playlistId
            is BrowseItem.Movie -> "MOVIE" to item.movie.playlistId
            is BrowseItem.Show -> "SERIES" to item.series.playlistId
        }
        viewModelScope.launch {
            repository.hideItem(HiddenItemEntity(item.key, playlistId, kind, item.title, item.imageUrl))
            message.value = uiText(R.string.home_item_hidden, item.title)
        }
    }

    fun unhideItem(contentKey: String) {
        viewModelScope.launch { repository.unhideItem(contentKey) }
    }

    // ============================ Control parental ============================

    fun unlockParental(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(parental.unlock(pin)) }
    }

    /** Comprueba el PIN sin desbloquear la sesión. */
    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(parental.checkPin(pin)) }
    }

    fun lockParental() {
        parental.lock()
        message.value = uiText(R.string.home_adult_locked)
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            parental.setPin(pin)
            message.value = uiText(R.string.home_pin_saved)
        }
    }

    fun clearPin(currentPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = parental.checkPin(currentPin)
            if (ok) {
                parental.clearPin()
                message.value = uiText(R.string.home_pin_removed)
            }
            onResult(ok)
        }
    }

    fun toggleFavorite(item: BrowseItem) {
        val favorite = when (item) {
            is BrowseItem.Live -> FavoriteEntity(item.channel.id, "LIVE", item.channel.playlistId, item.title, item.imageUrl)
            is BrowseItem.Movie -> FavoriteEntity(item.movie.id, "MOVIE", item.movie.playlistId, item.title, item.imageUrl)
            is BrowseItem.Show -> FavoriteEntity(item.series.id, "SERIES", item.series.playlistId, item.title, item.imageUrl)
        }
        viewModelScope.launch {
            val added = repository.toggleFavorite(favorite)
            message.value = uiText(if (added) R.string.home_added_my_list else R.string.home_removed_my_list, item.title)
        }
    }

    // ============================ Cuenta ============================

    private val _accountSaving = MutableStateFlow(false)
    val accountSaving: StateFlow<Boolean> = _accountSaving.asStateFlow()
    private val _accountError = MutableStateFlow<UiText?>(null)
    val accountError: StateFlow<UiText?> = _accountError.asStateFlow()

    fun clearAccountError() { _accountError.value = null }

    fun updateAccount(name: String, user: String, pass: String, onDone: () -> Unit) {
        val current = account.value ?: return
        if (user.isBlank() || pass.isBlank()) {
            _accountError.value = uiText(R.string.home_account_missing_fields)
            return
        }
        viewModelScope.launch {
            _accountSaving.value = true
            _accountError.value = null
            repository.updateAccount(current.id, name.trim(), user.trim(), pass.trim())
                .onSuccess {
                    onDone()
                    message.value = uiText(R.string.home_account_updated)
                    if (current.username != user.trim() || current.password != pass.trim()) refreshAll(manual = true)
                }
                .onFailure { _accountError.value = (it as? AccountException)?.text ?: uiText(R.string.home_account_save_failed) }
            _accountSaving.value = false
        }
    }

    /** Cierra la sesión; MainActivity vuelve sola al inicio de sesión al quedar sin cuentas. */
    fun logout() {
        val current = account.value ?: return
        viewModelScope.launch { repository.logout(current.id) }
    }

    init {
        viewModelScope.launch {
            if (repository.playlists.first().isNotEmpty()) {
                withTimeoutOrNull(AppStartup.MAX_WAIT_MS) {
                    content.first { it.rows.isNotEmpty() || it.categories.isNotEmpty() }
                }
            }
            AppStartup.markReady()
            // Mientras se ve la primera sección, deja listos los nombres limpios de Películas y
            // Series con prioridad baja: con miles de títulos, entrar ya no espera esa cuenta.
            val pid = selectedPlaylistId.first { it != null } ?: return@launch
            withContext(warmDispatcher) {
                val movies = movieTable.of(pid).first()
                val series = seriesTable.of(pid).first()
                movies.forEach { matchKey(it.name) }
                series.forEach { matchKey(it.name) }
                searchNames(movieNames, movies) { it.name }
                searchNames(seriesNames, series) { it.name }
                // La primera entrada a Películas o Series ya encuentra sus filas armadas.
                val keys = favoriteKeys.first()
                val o = options.first { it.playlistId == pid }
                val order = effectiveSort.first()
                val t = trending.value
                listOf(
                    RawCatalog(CatalogFilter(pid, Section.MOVIES, null, ""), movies = movies, favoriteKeys = keys),
                    RawCatalog(CatalogFilter(pid, Section.SERIES, null, ""), series = series, favoriteKeys = keys)
                ).forEach { r ->
                    if (synchronized(builtCache) { builtCache[r.filter] } != null) return@forEach
                    val built = build(r, o, order, t).copy(playlistId = pid, section = r.filter.section, category = null)
                    synchronized(builtCache) { builtCache.putIfAbsent(r.filter, BuiltContent(r, o, order, t, built)) }
                }
            }
        }
        // Selecciona la primera cuenta y, al entrar, actualiza en segundo plano con lo
        // guardado ya visible, solo si toca según la frecuencia de Configuración.
        viewModelScope.launch {
            repository.playlists.collect { list ->
                val selected = selectedPlaylistId.value
                if ((selected == null || list.none { it.id == selected }) && list.isNotEmpty()) {
                    selectedPlaylistId.value = list.first().id
                    refreshIfDue()
                }
            }
        }
        // Tendencias para el Top 10; se vuelven a pedir si llega una clave de TMDB nueva.
        viewModelScope.launch {
            settings.tmdbApiKey.distinctUntilChanged().collect { key ->
                if (key.isBlank()) return@collect
                val movies = metadata.trending(isSeries = false)
                val series = metadata.trending(isSeries = true)
                trending.value = Trending(movies, series)
            }
        }
    }

    fun selectPlaylist(id: String) {
        selectedPlaylistId.value = id
        category.value = null
        refreshIfDue()
    }

    private var autoRefresh: Job? = null

    /**
     * Al entrar o al volver a la app: baja el catálogo solo si pasó el corte de la mañana (o
     * lo pide Configuración). Lo guardado sigue a la vista mientras tanto.
     */
    fun refreshIfDue() {
        if (selectedPlaylistId.value == null || autoRefresh?.isActive == true) return
        autoRefresh = refreshAll(manual = false)
    }

    /** Elegir una sección (también la misma) lleva a su inicio. */
    fun selectSection(s: Section) {
        summary.value = false
        section.value = s
        category.value = null
    }

    fun selectCategory(c: String?) {
        summary.value = false
        category.value = c
    }

    /** Orden de esta sesión; elegir el de Configuración vuelve a seguirlo. */
    fun selectSort(order: SortOrder) {
        sort.value = if (order == SortOrder.fromName(catalogPrefs.value.defaultSort)) null else order
    }

    // ============================ Menú del póster ============================

    /** Serie: sigue según [SeriesResumeRule] o arranca por el primer episodio. */
    fun playSeries(series: SeriesEntity, onReady: (String) -> Unit) = playSeries(series.id, onReady)

    fun playSeries(seriesKey: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val series = repository.seriesById(seriesKey) ?: return@launch
            var episodes = repository.episodeSnapshot(series.id)
            if (episodes.isEmpty()) {
                repository.seriesDetails(series)
                episodes = repository.episodeSnapshot(series.id)
            }
            val progress = repository.observeSeriesProgress(series.id).first()
            val resume = SeriesResumeRule.resolve(episodes, progress)
            val target = resume.episode?.id ?: episodes.firstOrNull()?.id
            if (target != null) onReady(target)
            else message.value = uiText(R.string.home_no_episodes, clean(series.name, catalogPrefs.value.cleanNames))
        }
    }

    fun markWatched(movie: VodMovieEntity) {
        viewModelScope.launch {
            val duration = repository.progressFor(movie.id)?.durationMs?.takeIf { it > 0 } ?: 1L
            val title = clean(movie.name, catalogPrefs.value.cleanNames)
            repository.saveProgress(
                PlaybackProgressEntity(
                    contentKey = movie.id, kind = "MOVIE", playlistId = movie.playlistId,
                    refId = movie.id, title = title, imageUrl = movie.icon,
                    subtitle = movie.categoryName, positionMs = duration, durationMs = duration
                )
            )
            message.value = uiText(R.string.home_marked_watched, title)
        }
    }

    /** Saca el título de "Seguir viendo". En series no se borra lo visto: solo se esconde la tarjeta. */
    fun removeFromContinue(item: BrowseItem) {
        when (item) {
            is BrowseItem.Movie -> removeFromContinue(item.movie.id, isSeries = false, title = item.title)
            is BrowseItem.Show -> removeFromContinue(item.series.id, isSeries = true, title = item.title)
            is BrowseItem.Live -> Unit
        }
    }

    fun removeFromContinue(item: ContinueItem) = removeFromContinue(item.key, item.isSeries, item.title)

    private fun removeFromContinue(key: String, isSeries: Boolean, title: String) {
        viewModelScope.launch {
            if (isSeries) settings.dismissContinue(key, System.currentTimeMillis())
            else repository.removeProgress(key)
            message.value = uiText(R.string.home_removed_continue, title)
        }
    }

    fun resetProgress(item: BrowseItem) {
        when (item) {
            is BrowseItem.Show -> resetSeries(item.series)
            is BrowseItem.Movie -> viewModelScope.launch {
                repository.removeProgress(item.movie.id)
                message.value = uiText(R.string.home_progress_reset, item.title)
            }
            is BrowseItem.Live -> Unit
        }
    }

    fun onQueryChange(q: String) {
        val wasEmpty = query.value.isBlank()
        query.value = q
        // Empezar a buscar o borrar la búsqueda sale de la categoría o del "Ver todo" anterior.
        if (wasEmpty != q.isBlank()) category.value = null
    }

    fun consumeMessage() { message.value = null }

    /**
     * Actualiza el catálogo. Al entrar es silenciosa y respeta la frecuencia de Configuración
     * (solo el indicador pequeño si de verdad baja algo); a mano ([manual]) siempre baja todo.
     */
    fun refreshAll(manual: Boolean = true): Job =
        viewModelScope.launch {
            if (manual) refreshing.value = true
            val pid = selectedPlaylistId.value
            val first = pid != null && repository.needsFirstSync(pid)
            if (first) firstSync.value = true
            repository.refreshAll(force = manual)
                .onSuccess { failed ->
                    if (failed.isNotEmpty() && (manual || first)) {
                        val list = failed.reduce { acc, item -> uiText(R.string.home_refresh_list_join, acc, item) }
                        message.value = uiText(R.string.home_refresh_failed, list)
                    }
                }
                .onFailure {
                    if (manual || first) message.value = uiText(R.string.home_refresh_failed_offline)
                }
            refreshing.value = false
            firstSync.value = false
        }

    fun episodesFor(seriesKey: String) = repository.episodes(seriesKey)

    /** Abre una serie: descarga sus episodios si hace falta. */
    fun openSeries(series: SeriesEntity) {
        viewModelScope.launch {
            repository.syncSeriesEpisodes(series)
        }
    }

    // ===================== Gestión de progreso (Netflix) =====================

    fun resetSeries(series: SeriesEntity) {
        viewModelScope.launch {
            repository.resetSeries(series.id)
            message.value = uiText(R.string.home_series_reset, clean(series.name, catalogPrefs.value.cleanNames))
        }
    }

    fun resetAllProgress() {
        viewModelScope.launch {
            repository.resetAllProgress()
            message.value = uiText(R.string.home_all_progress_reset)
        }
    }
}
