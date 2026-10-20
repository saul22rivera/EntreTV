package com.entretv.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entretv.app.data.download.DownloadRepository
import com.entretv.app.data.local.CategoryPrefEntity
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.FavoriteEntity
import com.entretv.app.data.local.HiddenItemEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.local.PlaylistEntity
import com.entretv.app.data.local.SeriesEntity
import com.entretv.app.data.local.VodMovieEntity
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.CatalogPrefs
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
data class ContentRow(val title: String, val items: List<BrowseItem>, val label: String = title)

enum class Section(val label: String) {
    LIVE("En vivo"), MOVIES("Películas"), SERIES("Series"), FAVORITES("Mi lista")
}

/** Orden del catálogo. [ORIGINAL] respeta el orden en que lo manda el servidor. */
enum class SortOrder(val label: String) {
    ORIGINAL("Original"), AZ("A-Z"), RATING("Puntuación"), RECENT("Recientes");

    companion object {
        fun fromName(name: String): SortOrder = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

const val MY_LIST_ROW = "Mi lista"
const val RECENT_ROW = "Recién agregados"
const val TOP_RATED_ROW = "Mejor valorados"

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
 * cuadrícula completos (categoría elegida con "Ver todo" o resultados de búsqueda).
 */
data class SectionContent(
    val categories: List<String> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val categoryLabels: Map<String, String> = emptyMap(),
    /** Categorías de adultos bloqueadas por el PIN: piden el PIN al elegirlas. */
    val lockedCategories: Set<String> = emptySet(),
    val rows: List<ContentRow> = emptyList(),
    val grid: Boolean = false,
    val featured: List<BrowseItem> = emptyList(),
    /** Órdenes que tienen sentido para lo que se ve (en vivo no trae fecha ni puntuación). */
    val sortOptions: List<SortOrder> = emptyList(),
    /** Lista, sección y categoría para las que se calculó; null mientras no hay nada calculado. */
    val playlistId: String? = null,
    val section: Section? = null,
    val category: String? = null
)

data class HomeUiState(
    val playlists: List<PlaylistEntity> = emptyList(),
    val selectedPlaylistId: String? = null,
    val query: String = "",
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val message: String? = null,
    val section: Section = Section.LIVE,
    /** null = "Todas". */
    val category: String? = null,
    val sort: SortOrder = SortOrder.ORIGINAL
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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val settings: SettingsRepository,
    private val parental: ParentalControl,
    private val downloads: DownloadRepository
) : ViewModel() {

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

    private val selectedPlaylistId = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val section = MutableStateFlow(Section.LIVE)
    private val category = MutableStateFlow<String?>(null)
    /** Orden elegido en esta sesión; null = el de Configuración. */
    private val sort = MutableStateFlow<SortOrder?>(null)

    private val effectiveSort: Flow<SortOrder> =
        combine(sort, settings.catalogPrefs) { s, p -> s ?: SortOrder.fromName(p.defaultSort) }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.playlists, selectedPlaylistId, query, loading, refreshing, message, section, category, effectiveSort
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        HomeUiState(
            playlists = arr[0] as List<PlaylistEntity>,
            selectedPlaylistId = arr[1] as String?,
            query = arr[2] as String,
            loading = arr[3] as Boolean,
            refreshing = arr[4] as Boolean,
            message = arr[5] as String?,
            section = arr[6] as Section,
            category = arr[7] as String?,
            sort = arr[8] as SortOrder
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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

    /** "Seguir viendo" de la sección activa. En vivo no guarda minuto; las películas terminadas salen. */
    val continueWatching: StateFlow<List<PlaybackProgressEntity>> =
        combine(repository.continueWatching, section, hiddenKeys, adultContentKeys) { list, s, hidden, adult ->
            val visible = list.filter {
                val owner = it.contentKey.substringBefore('#')
                owner !in hidden && owner !in adult
            }
            when (s) {
                Section.LIVE, Section.FAVORITES -> emptyList()
                Section.MOVIES -> visible.filter { it.kind == "MOVIE" && !it.isCompleted }
                Section.SERIES -> visible.filter { it.kind == "EPISODE" }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val favoriteKeys = repository.favorites.map { list -> list.map { it.contentKey } }

    val favoriteKeySet: StateFlow<Set<String>> = favoriteKeys.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Solo se observa la tabla de la sección activa. La búsqueda y "Mi lista" sí
     * cruzan canales, películas y series.
     */
    val content: StateFlow<SectionContent> =
        combine(selectedPlaylistId, section, category, query.debounce(250)) { pid, s, c, q ->
            CatalogFilter(pid, s, c, q.trim())
        }
            .flatMapLatest { f ->
                val pid = f.playlistId ?: return@flatMapLatest flowOf(RawCatalog(f))
                when {
                    f.query.isNotEmpty() -> combine(
                        repository.channels(pid), repository.movies(pid), repository.series(pid)
                    ) { channels, movies, series -> RawCatalog(f, channels, movies, series) }

                    f.section == Section.FAVORITES -> combine(
                        repository.channels(pid), repository.movies(pid), repository.series(pid), favoriteKeys
                    ) { channels, movies, series, keys -> RawCatalog(f, channels, movies, series, keys) }

                    f.section == Section.LIVE -> combine(repository.channels(pid), favoriteKeys) { list, keys ->
                        RawCatalog(f, channels = list, favoriteKeys = keys)
                    }
                    f.section == Section.MOVIES -> combine(repository.movies(pid), favoriteKeys) { list, keys ->
                        RawCatalog(f, movies = list, favoriteKeys = keys)
                    }
                    else -> combine(repository.series(pid), favoriteKeys) { list, keys ->
                        RawCatalog(f, series = list, favoriteKeys = keys)
                    }
                }
            }
            .let { raw ->
                combine(raw, options, effectiveSort) { r, o, order ->
                    build(r, o, order).copy(
                        playlistId = r.filter.playlistId,
                        section = r.filter.section,
                        category = r.filter.category
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionContent())

    private fun build(r: RawCatalog, o: CatalogOptions, order: SortOrder): SectionContent {
        val f = r.filter
        if (f.playlistId == null) return SectionContent()
        val c = o.cleanNames
        return when {
            f.query.isNotEmpty() -> searchAll(r, o, order)
            f.section == Section.FAVORITES -> myList(r, o, order)
            f.section == Section.LIVE -> buildContent(
                r.channels, PlaylistRepository.SECTION_LIVE, f, r.favoriteKeys, o, order, featured = false,
                { it.groupTitle.ifEmpty { "En vivo" } }, { it.id }
            ) { BrowseItem.Live(it, clean(it.name, c)) }
            f.section == Section.MOVIES -> buildContent(
                r.movies, PlaylistRepository.SECTION_MOVIES, f, r.favoriteKeys, o, order, featured = true,
                { it.categoryName.ifEmpty { "Películas" } }, { it.id }
            ) { BrowseItem.Movie(it, clean(it.name, c)) }
            else -> buildContent(
                r.series, PlaylistRepository.SECTION_SERIES, f, r.favoriteKeys, o, order, featured = true,
                { it.categoryName.ifEmpty { "Series" } }, { it.id }
            ) { BrowseItem.Show(it, clean(it.name, c)) }
        }
    }

    private val cleanCache = ConcurrentHashMap<String, String>()

    private fun clean(raw: String, enabled: Boolean): String =
        if (!enabled) raw else cleanCache.getOrPut(raw) { TitleCleaner.clean(raw) }

    private fun sortItems(items: List<BrowseItem>, order: SortOrder): List<BrowseItem> = when (order) {
        SortOrder.ORIGINAL -> items
        SortOrder.RECENT -> items.sortedByDescending { it.added }
        SortOrder.AZ -> items.sortedBy { normalize(it.title) }
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
        featured: Boolean,
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
        val labels = shown.associateWith { clean(it, o.cleanNames) }

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

        if (f.category != null) {
            val list = when (f.category) {
                RECENT_ROW -> if (order == SortOrder.ORIGINAL) recent else sortItems(recent, order)
                TOP_RATED_ROW -> if (order == SortOrder.ORIGINAL) topRated else sortItems(topRated, order)
                else -> groupItems(f.category, Int.MAX_VALUE)
            }
            return SectionContent(
                categories = shown,
                categoryCounts = counts,
                categoryLabels = labels,
                lockedCategories = locked,
                rows = listOf(ContentRow(f.category, list, labels[f.category] ?: f.category)),
                grid = true,
                sortOptions = sortOptions
            )
        }
        val byKey = visibleRaw.values.flatten().associateBy(keyOf)
        val mine = favoriteKeys.mapNotNull { byKey[it] }.map(wrap)
        val rows = buildList {
            if (mine.isNotEmpty()) add(ContentRow(MY_LIST_ROW, mine))
            // Solo si el servidor informa fechas o puntuaciones de verdad.
            if (featured && recent.size >= 5) add(ContentRow(RECENT_ROW, recent.take(20)))
            if (featured && topRated.size >= 5) add(ContentRow(TOP_RATED_ROW, topRated.take(20)))
            visibleRaw.keys.forEach { group ->
                val list = groupItems(group, 20)
                if (list.isNotEmpty()) add(ContentRow(group, list, labels[group] ?: group))
            }
        }
        // Destacados estables durante el día: cambian a diario sin saltar en cada refresco.
        val highlights = if (featured) {
            val day = System.currentTimeMillis() / 86_400_000L
            wrapped.filter { it.imageUrl.isNotEmpty() }.shuffled(Random(day)).take(6)
        } else emptyList()
        return SectionContent(
            categories = shown,
            categoryCounts = counts,
            categoryLabels = labels,
            lockedCategories = locked,
            rows = rows,
            featured = highlights,
            sortOptions = sortOptions
        )
    }

    private fun searchAll(r: RawCatalog, o: CatalogOptions, order: SortOrder): SectionContent {
        val tokens = normalize(r.filter.query).split(' ').filter { it.isNotBlank() }
        fun matches(name: String): Boolean {
            val n = normalize(name)
            return tokens.all { n.contains(it) }
        }
        val skipLive = excluder(PlaylistRepository.SECTION_LIVE, o)
        val skipMovie = excluder(PlaylistRepository.SECTION_MOVIES, o)
        val skipSeries = excluder(PlaylistRepository.SECTION_SERIES, o)
        val c = o.cleanNames
        val movies = r.movies.filter { it.id !in o.hidden && !skipMovie(it.categoryName) && matches(it.name) }
            .map { BrowseItem.Movie(it, clean(it.name, c)) }
        val series = r.series.filter { it.id !in o.hidden && !skipSeries(it.categoryName) && matches(it.name) }
            .map { BrowseItem.Show(it, clean(it.name, c)) }
        val channels = r.channels.filter { it.id !in o.hidden && !skipLive(it.groupTitle) && matches(it.name) }
            .map { BrowseItem.Live(it, clean(it.name, c)) }
        val rows = listOf(
            ContentRow("Películas", sortItems(movies, order)),
            ContentRow("Series", sortItems(series, order)),
            ContentRow("Canales en vivo", sortItems(channels, orderFor(PlaylistRepository.SECTION_LIVE, order)))
        )
        return SectionContent(rows = rows, grid = true, sortOptions = SortOrder.entries.toList())
    }

    private fun myList(r: RawCatalog, o: CatalogOptions, order: SortOrder): SectionContent {
        val keys = r.favoriteKeys
        val wanted = keys.toSet()
        val skipLive = excluder(PlaylistRepository.SECTION_LIVE, o)
        val skipMovie = excluder(PlaylistRepository.SECTION_MOVIES, o)
        val skipSeries = excluder(PlaylistRepository.SECTION_SERIES, o)
        fun <T> pick(list: List<T>, key: (T) -> String, skip: (T) -> Boolean) =
            list.filter { key(it) in wanted && key(it) !in o.hidden && !skip(it) }.sortedBy { keys.indexOf(key(it)) }
        val c = o.cleanNames
        val movies = pick(r.movies, { it.id }, { skipMovie(it.categoryName) }).map { BrowseItem.Movie(it, clean(it.name, c)) }
        val series = pick(r.series, { it.id }, { skipSeries(it.categoryName) }).map { BrowseItem.Show(it, clean(it.name, c)) }
        val channels = pick(r.channels, { it.id }, { skipLive(it.groupTitle) }).map { BrowseItem.Live(it, clean(it.name, c)) }
        return SectionContent(
            rows = listOf(
                ContentRow("Películas", sortItems(movies, order)),
                ContentRow("Series", sortItems(series, order)),
                ContentRow("Canales", sortItems(channels, orderFor(PlaylistRepository.SECTION_LIVE, order)))
            ),
            grid = true,
            sortOptions = SortOrder.entries.toList()
        )
    }

    private val accents = Regex("\\p{Mn}+")

    private fun normalize(text: String): String =
        accents.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")

    // ============================ Organizar categorías ============================

    private fun sectionKey(s: Section): String? = when (s) {
        Section.LIVE -> PlaylistRepository.SECTION_LIVE
        Section.MOVIES -> PlaylistRepository.SECTION_MOVIES
        Section.SERIES -> PlaylistRepository.SECTION_SERIES
        Section.FAVORITES -> null
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
        organizeSection.value = if (s == Section.FAVORITES) Section.LIVE else s
    }

    private fun managedCategories(s: Section, o: CatalogOptions, order: SortOrder): List<CategoryInfo> {
        val key = sectionKey(s) ?: return emptyList()
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
        val key = sectionKey(s) ?: return
        savePrefs(listOf(prefFor(key, name).copy(hidden = hidden)))
        if (hidden && s == section.value && category.value == name) category.value = null
        if (hidden) message.value = "Categoría “${clean(name, true)}” oculta. Volvé a mostrarla desde Organizar categorías."
    }

    fun setCategoryAdult(s: Section, name: String, adult: Boolean) {
        val key = sectionKey(s) ?: return
        savePrefs(listOf(prefFor(key, name).copy(adult = adult)))
        if (adult && !pinSet.value) {
            message.value = "Marcada como adultos. Poné un PIN en Configuración para bloquearla."
        }
    }

    /** Sube (-1) o baja (+1) una categoría; desde ahí el orden pasa a ser el del usuario. */
    fun moveCategory(s: Section, name: String, delta: Int) {
        val key = sectionKey(s) ?: return
        val names = managedCategories(s, options.value, uiState.value.sort).map { it.name }.toMutableList()
        val from = names.indexOf(name)
        val to = from + delta
        if (from < 0 || to !in names.indices) return
        names.add(to, names.removeAt(from))
        savePrefs(names.mapIndexed { index, n -> prefFor(key, n).copy(userPosition = index) })
    }

    fun resetCategoryOrder(s: Section) {
        val key = sectionKey(s) ?: return
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
            message.value = "“${item.title}” oculto. Podés volver a mostrarlo desde Configuración."
        }
    }

    fun unhideItem(contentKey: String) {
        viewModelScope.launch { repository.unhideItem(contentKey) }
    }

    // ============================ Control parental ============================

    fun unlockParental(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(parental.unlock(pin)) }
    }

    fun lockParental() {
        parental.lock()
        message.value = "Contenido para adultos bloqueado"
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            parental.setPin(pin)
            message.value = "PIN guardado. Las categorías de adultos quedan bloqueadas."
        }
    }

    fun clearPin(currentPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = parental.checkPin(currentPin)
            if (ok) {
                parental.clearPin()
                message.value = "PIN quitado"
            }
            onResult(ok)
        }
    }

    // ============================ Fondos ============================

    // Fondos panorámicos de los destacados: se piden en segundo plano y se cachean.
    private val _backdrops = MutableStateFlow<Map<String, String>>(emptyMap())
    val backdrops: StateFlow<Map<String, String>> = _backdrops

    private fun prefetchBackdrops(items: List<BrowseItem>) {
        items.filter { it.key !in _backdrops.value }.forEach { item ->
            viewModelScope.launch {
                val details = when (item) {
                    is BrowseItem.Movie -> repository.movieDetails(item.movie).getOrNull()
                    is BrowseItem.Show -> repository.cachedDetails(item.series.id)
                        ?: repository.seriesDetails(item.series).getOrNull()
                    is BrowseItem.Live -> null
                }
                val url = details?.backdropUrl.orEmpty()
                if (url.isNotEmpty()) _backdrops.value = _backdrops.value + (item.key to url)
            }
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
            message.value = if (added) "“${item.title}” agregado a Mi lista" else "“${item.title}” quitado de Mi lista"
        }
    }

    init {
        viewModelScope.launch {
            content.collect { prefetchBackdrops(it.featured) }
        }
        viewModelScope.launch {
            if (repository.playlists.first().isNotEmpty()) {
                withTimeoutOrNull(AppStartup.MAX_WAIT_MS) {
                    content.first { it.rows.isNotEmpty() || it.categories.isNotEmpty() }
                }
            }
            AppStartup.markReady()
        }
        // Selecciona la primera lista y, al entrar cada vez, refresca en segundo
        // plano con los datos cacheados ya visibles (rápido).
        viewModelScope.launch {
            repository.playlists.collect { list ->
                if (selectedPlaylistId.value == null && list.isNotEmpty()) {
                    selectedPlaylistId.value = list.first().id
                    refreshAll(silent = true)
                }
            }
        }
    }

    fun selectPlaylist(id: String) {
        selectedPlaylistId.value = id
        category.value = null
        refreshAll(silent = true)
    }

    fun selectSection(s: Section) {
        if (section.value == s) return
        section.value = s
        category.value = null
    }

    fun selectCategory(c: String?) { category.value = c }

    /** Orden de esta sesión; elegir el de Configuración vuelve a seguirlo. */
    fun selectSort(order: SortOrder) {
        sort.value = if (order == SortOrder.fromName(catalogPrefs.value.defaultSort)) null else order
    }

    // ============================ Menú del póster ============================

    /** Serie: sigue en el episodio en curso o arranca por el primero. */
    fun playSeries(series: SeriesEntity, onReady: (String) -> Unit) {
        viewModelScope.launch {
            var episodes = repository.episodeSnapshot(series.id)
            if (episodes.isEmpty()) {
                repository.seriesDetails(series)
                episodes = repository.episodeSnapshot(series.id)
            }
            val inProgress = repository.continueWatching.first()
                .firstOrNull { it.kind == "EPISODE" && it.contentKey.startsWith("${series.id}#") }
            val target = inProgress?.contentKey?.takeIf { key -> episodes.any { it.id == key } }
                ?: episodes.firstOrNull()?.id
            if (target != null) onReady(target)
            else message.value = "“${series.name}” todavía no tiene episodios disponibles"
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
            message.value = "“$title” marcada como vista"
        }
    }

    /** Saca el título de "Seguir viendo" (en series, los episodios a medias). */
    fun removeFromContinue(item: BrowseItem) {
        viewModelScope.launch {
            when (item) {
                is BrowseItem.Movie -> repository.removeProgress(item.movie.id)
                is BrowseItem.Show -> repository.continueWatching.first()
                    .filter { it.contentKey.startsWith("${item.series.id}#") && !it.isCompleted }
                    .forEach { repository.removeProgress(it.contentKey) }
                is BrowseItem.Live -> Unit
            }
            message.value = "“${item.title}” quitado de Seguir viendo"
        }
    }

    fun resetProgress(item: BrowseItem) {
        when (item) {
            is BrowseItem.Show -> resetSeries(item.series)
            is BrowseItem.Movie -> viewModelScope.launch {
                repository.removeProgress(item.movie.id)
                message.value = "“${item.title}” volverá a empezar desde el principio"
            }
            is BrowseItem.Live -> Unit
        }
    }

    fun onQueryChange(q: String) { query.value = q }

    fun consumeMessage() { message.value = null }

    /** Sincroniza todas las listas: se muestra spinner sutil, nunca bloquea. */
    fun refreshAll(silent: Boolean = false) {
        viewModelScope.launch {
            if (silent) refreshing.value = true else loading.value = true
            repository.refreshAll()
                .onSuccess { failed ->
                    if (failed.isNotEmpty()) {
                        message.value = "No se pudo actualizar: ${failed.joinToString(", ")}. Se muestra lo guardado."
                    }
                }
                .onFailure { message.value = "No se pudo actualizar: ${it.message ?: "error de red"}. Se muestra lo guardado." }
            refreshing.value = false
            loading.value = false
        }
    }

    fun episodesFor(seriesKey: String) = repository.episodes(seriesKey)

    /** Abre una serie: descarga sus episodios si hace falta. */
    fun openSeries(series: SeriesEntity) {
        viewModelScope.launch {
            repository.syncSeriesEpisodes(series)
        }
    }

    // ===================== Gestión de progreso (Netflix) =====================

    fun removeFromContinue(contentKey: String) {
        viewModelScope.launch { repository.removeProgress(contentKey) }
    }

    fun resetSeries(series: SeriesEntity) {
        viewModelScope.launch {
            repository.resetSeries(series.id)
            message.value = "Serie reiniciada: “${clean(series.name, catalogPrefs.value.cleanNames)}” volverá al episodio 1"
        }
    }

    fun resetAllProgress() {
        viewModelScope.launch {
            repository.resetAllProgress()
            message.value = "Todo el progreso fue borrado"
        }
    }

    fun addM3u(name: String, url: String) = launchAdd { repository.addM3uPlaylist(name, url) }

    fun addXtream(name: String, baseUrl: String, user: String, pass: String) =
        launchAdd { repository.addXtreamPlaylist(name, baseUrl, user, pass) }

    private fun launchAdd(block: suspend () -> Result<String>) {
        viewModelScope.launch {
            loading.value = true
            block().onSuccess { id ->
                selectedPlaylistId.value = id
                message.value = "Lista agregada correctamente"
            }.onFailure { message.value = it.message ?: "Error al agregar la lista" }
            loading.value = false
        }
    }
}
