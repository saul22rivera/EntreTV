package com.entretv.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.home.ContentAction
import com.entretv.app.ui.home.ContentMenu
import com.entretv.app.ui.home.AccountDialog
import com.entretv.app.ui.home.HiddenItemsDialog
import com.entretv.app.ui.home.NavRail
import com.entretv.app.ui.home.SectionTabs
import com.entretv.app.ui.home.OrganizeCategoriesDialog
import com.entretv.app.ui.home.PinDialog
import com.entretv.app.ui.home.SettingsPanel
import com.entretv.app.ui.home.PILL_SPACE
import com.entretv.app.ui.home.RAIL_COLLAPSED_WIDTH
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreWordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Si la tira de íconos recibe el foco dentro de este tiempo tras una flecha izquierda, la llevó el usuario. */
private const val STRIP_KEY_WINDOW_MS = 700L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun HomeScreen(
    onOpen: (BrowseItem) -> Unit,
    onPlayMovie: (String) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()
    val ui by viewModel.uiState.collectAsState()
    val content by viewModel.content.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val favorites by viewModel.favoriteKeySet.collectAsState()
    val account by viewModel.account.collectAsState()
    val accountSaving by viewModel.accountSaving.collectAsState()
    val accountError by viewModel.accountError.collectAsState()
    val bufferSeconds by viewModel.bufferSeconds.collectAsState()
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsState()
    val maxBufferSeconds by viewModel.maxBufferSeconds.collectAsState()
    val catalogPrefs by viewModel.catalogPrefs.collectAsState()
    val pinSet by viewModel.pinSet.collectAsState()
    val parentalUnlocked by viewModel.parentalUnlocked.collectAsState()
    val hiddenItems by viewModel.hiddenItems.collectAsState()
    val organizeList by viewModel.organizeList.collectAsState()
    val organizingSection by viewModel.organizingSection.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmAdult by remember { mutableStateOf<GuardedChange?>(null) }
    var showResetAll by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showOrganize by rememberSaveable { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf<String?>(null) }
    var pinStep by remember { mutableStateOf<PinStep?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searching = ui.query.isNotBlank()

    var railExpanded by rememberSaveable { mutableStateOf(false) }
    val pillVisible = rememberSaveable { mutableStateOf(true) }
    val catalogFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var catalogHasFocus by remember { mutableStateOf(false) }
    // Hora de la última flecha izquierda: distingue al usuario que va al menú del foco que
    // el sistema reubica solo al cerrarse el panel.
    var lastLeftKeyAt by remember { mutableStateOf(0L) }
    var menuItem by remember { mutableStateOf<BrowseItem?>(null) }
    var focusedItem by remember { mutableStateOf<BrowseItem?>(null) }
    // Póster enfocado al salir a la ficha: al volver (Inicio se recrea) se le devuelve el foco en TV.
    // No es un estado observable: cambiar de póster no debe recomponer el catálogo entero.
    val focusHolder = rememberSaveable(saver = FocusHolder.Saver) { FocusHolder() }
    val restoreFocus = remember { FocusRequester() }
    var focusRestored by remember { mutableStateOf(false) }
    val focusMemory = remember {
        FocusMemory(requester = restoreFocus, target = focusHolder.key, onFocused = { focusHolder.key = it })
    }

    // En celular, bajar esconde la píldora y subir la devuelve.
    val hidePillOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) pillVisible.value = false
                else if (available.y > 8f) pillVisible.value = true
                return Offset.Zero
            }
        }
    }

    fun setRailExpanded(value: Boolean) {
        railExpanded = value
    }

    val contentStale = content.playlistId != ui.selectedPlaylistId ||
        content.section != ui.section || content.category != ui.category

    // En TV el foco vuelve a los pósters al abrir la app, al cerrar el panel y al cambiar de
    // sección. Pedirlo dentro del cambio de foco que cierra el panel deja el árbol de foco a
    // medias (el póster queda marcado pero las flechas no responden): se espera un frame.
    // Al volver de otra pantalla además se espera a que termine la transición (RESUMED): al
    // quitarse la pantalla anterior Android le devuelve el foco a la vista y el catálogo queda
    // "con foco" sin ningún póster enfocado, así que esa primera vez no se confía en catalogHasFocus.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(railExpanded, contentStale) {
        if (!railExpanded && !contentStale && profile.isTv) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            withFrameNanos { }
            if (focusRestored && catalogHasFocus) return@LaunchedEffect
            if (!focusRestored && focusMemory.target != null) {
                focusRestored = true
                val restored = runCatching { restoreFocus.requestFocus() }.isSuccess
                withFrameNanos { }
                if (restored && catalogHasFocus) return@LaunchedEffect
            }
            focusRestored = true
            runCatching { catalogFocus.requestFocus() }
        }
    }

    fun openSearch() {
        searchOpen = true
        pillVisible.value = true
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(150)
            runCatching { searchFocus.requestFocus() }
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            viewModel.consumeMessage()
        }
    }

    // Atrás primero cierra el "Ver todo" (también el de la búsqueda), después la búsqueda y
    // por último sale de la app.
    BackHandler(enabled = searchOpen || ui.category != null) {
        when {
            ui.category != null -> viewModel.selectCategory(null)
            else -> {
                viewModel.onQueryChange("")
                searchOpen = false
            }
        }
    }

    val onItemClick: (BrowseItem) -> Unit = onOpen
    val onItemLongClick: (BrowseItem) -> Unit = { menuItem = it }

    /** Las categorías de adultos bloqueadas piden el PIN antes de abrirse. */
    fun openCategory(name: String?) {
        if (name != null && name in content.lockedCategories) {
            pinError = null
            pinStep = PinStep.Unlock(name)
        } else viewModel.selectCategory(name)
    }

    fun openOrganize() {
        viewModel.organize(ui.section)
        showOrganize = true
    }

    /**
     * Marcar como adultos pide confirmación; quitar la marca (o mostrar una categoría de
     * adultos oculta) pide el PIN, o crearlo si todavía no hay.
     */
    fun requestAdultChange(change: GuardedChange) {
        pinError = null
        when {
            change.kind == GuardedChange.Kind.MARK_ADULT -> confirmAdult = change
            pinSet -> pinStep = PinStep.Guard(change)
            else -> pinStep = PinStep.GuardCreate(change)
        }
    }

    fun applyChange(change: GuardedChange) {
        when (change.kind) {
            GuardedChange.Kind.MARK_ADULT -> viewModel.setCategoryAdult(change.section, change.name, true)
            GuardedChange.Kind.UNMARK_ADULT -> viewModel.setCategoryAdult(change.section, change.name, false)
            GuardedChange.Kind.UNHIDE -> viewModel.setCategoryHidden(change.section, change.name, false)
        }
    }

    fun playContinue(item: ContinueItem) {
        val key = item.playKey
        when {
            !item.isSeries -> onPlayMovie(item.key)
            key != null -> onPlayEpisode(key)
            else -> viewModel.playSeries(item.key, onPlayEpisode)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) lastLeftKeyAt = SystemClock.uptimeMillis()
                    false
                }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = if (profile.compactRail || ui.playlists.isEmpty()) 0.dp else RAIL_COLLAPSED_WIDTH)
            ) {
                HomeHeader(
                    profile = profile,
                    refreshing = ui.refreshing,
                    searchOpen = searchOpen,
                    onToggleSearch = {
                        if (searchOpen) viewModel.onQueryChange("")
                        searchOpen = !searchOpen
                    },
                    onRefresh = { viewModel.refreshAll() },
                    onClearImages = {
                        coil.Coil.imageLoader(context).memoryCache?.clear()
                        coil.Coil.imageLoader(context).diskCache?.clear()
                        scope.launch { snackbar.showSnackbar("Caché de imágenes borrado") }
                    },
                    onResetAll = { showResetAll = true },
                    onSettings = { showSettings = true },
                    onDownloads = onOpenDownloads
                )

                if (profile.compactRail) {
                    SectionTabs(
                        section = ui.section,
                        searchActive = searching,
                        onSection = {
                            if (searchOpen) {
                                viewModel.onQueryChange("")
                                searchOpen = false
                            }
                            viewModel.selectSection(it)
                        },
                        modifier = Modifier.padding(horizontal = profile.edge, vertical = 4.dp)
                    )
                }

                AnimatedVisibility(
                    visible = searchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = profile.edge, vertical = 4.dp)
                            .focusRequester(searchFocus),
                        placeholder = { Text("Buscar películas, series y canales…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (ui.query.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Borrar")
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp)
                    )
                }

                if (ui.playlists.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = profile.edge, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ui.playlists, key = { it.id }) { playlist ->
                            FilterChip(
                                selected = playlist.id == ui.selectedPlaylistId,
                                onClick = { viewModel.selectPlaylist(playlist.id) },
                                label = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                PullToRefreshBox(
                    isRefreshing = ui.refreshing,
                    onRefresh = { viewModel.refreshAll() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val catalogModifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(hidePillOnScroll)
                        .onFocusChanged {
                            catalogHasFocus = it.hasFocus
                            // En TV, volver con el D-pad a los pósters cierra el panel.
                            if (it.hasFocus && railExpanded) railExpanded = false
                        }
                        .focusRequester(catalogFocus)
                        // Vuelve al último póster enfocado. No se usa focusRestorer(): en Compose 1.7
                        // fija ese póster y, si la lista cambia (otra sección), lo libera dos veces y la app se cierra.
                        .focusProperties {
                            exit = {
                                catalogFocus.saveFocusedChild()
                                FocusRequester.Default
                            }
                            enter = {
                                if (catalogFocus.restoreFocusedChild()) FocusRequester.Cancel else FocusRequester.Default
                            }
                        }
                        .focusGroup()
                        // Tecla Menú del control: abre el menú del póster enfocado.
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                                focusedItem?.let { menuItem = it }
                                true
                            } else false
                        }
                    val bottomSpace = if (profile.compactRail) PILL_SPACE else 32.dp
                    val nothingYet = content.rows.isEmpty() && content.categories.isEmpty()
                    when {
                        // Mientras se calcula la nueva sección no se muestran las filas de la anterior:
                        // el foco caería en un póster que desaparece y las flechas dejarían de responder.
                        contentStale || ui.playlists.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        ui.firstSync && nothingYet && !searching -> PreparingCatalog()
                        content.grid -> CatalogGrid(
                            profile = profile,
                            content = content,
                            title = when (ui.category) {
                                SEARCH_MOVIES -> "Películas para “${ui.query.trim()}”"
                                SEARCH_SERIES -> "Series para “${ui.query.trim()}”"
                                SEARCH_LIVE -> "Canales para “${ui.query.trim()}”"
                                MY_LIST_ROW -> "Mi lista · ${ui.section.label}"
                                else -> ui.category?.let { content.categoryLabels[it] ?: it }.orEmpty()
                            },
                            emptyText = when {
                                searching -> "No encontramos nada con “${ui.query.trim()}”"
                                ui.category == MY_LIST_ROW -> "Tu lista está vacía. Mantén presionado un póster o usa “Mi lista” en la ficha para guardarlo aquí."
                                else -> "Esta categoría está vacía"
                            },
                            showBack = ui.category != null,
                            onBack = { viewModel.selectCategory(null) },
                            stateKey = "${ui.section}|${ui.category}|${ui.query.trim()}",
                            sort = ui.sort,
                            onSort = viewModel::selectSort,
                            bottomSpace = bottomSpace,
                            focusMemory = focusMemory,
                            onClick = onItemClick,
                            onLongClick = onItemLongClick,
                            onFocusItem = { focusedItem = it },
                            modifier = catalogModifier
                        )
                        else -> CatalogRows(
                            profile = profile,
                            section = ui.section,
                            content = content,
                            continueWatching = if (searching) emptyList() else continueWatching,
                            emptyText = if (searching) "No encontramos nada con “${ui.query.trim()}”"
                            else "Todavía no hay contenido en ${ui.section.label}",
                            stateKey = if (searching) "search" else ui.section.name,
                            bottomSpace = bottomSpace,
                            focusMemory = focusMemory,
                            onClick = onItemClick,
                            onLongClick = onItemLongClick,
                            onFocusItem = { focusedItem = it },
                            onPlayContinue = ::playContinue,
                            onRemoveContinue = viewModel::removeFromContinue,
                            onSeeAll = { row -> openCategory(row.title) },
                            modifier = catalogModifier
                        )
                    }
                }
            }

            if (ui.playlists.isNotEmpty()) {
                NavRail(
                    profile = profile,
                    section = ui.section,
                    searchActive = searchOpen,
                    categories = if (searching) emptyList() else content.categories,
                    categoryCounts = content.categoryCounts,
                    categoryLabels = content.categoryLabels,
                    lockedCategories = content.lockedCategories,
                    selectedCategory = ui.category,
                    showCategories = !searching,
                    myListCount = content.myListCount,
                    expanded = railExpanded,
                    pillVisible = pillVisible.value,
                    onExpandedChange = ::setRailExpanded,
                    onSearch = ::openSearch,
                    onSection = {
                        if (searchOpen) {
                            viewModel.onQueryChange("")
                            searchOpen = false
                        }
                        viewModel.selectSection(it)
                        if (!profile.isTv) setRailExpanded(false)
                    },
                    onCategory = ::openCategory,
                    onCategoryMenu = {
                        viewModel.organize(ui.section)
                        categoryMenu = it
                    },
                    onOrganize = ::openOrganize,
                    onSettings = { showSettings = true },
                    onDownloads = {
                        setRailExpanded(false)
                        onOpenDownloads()
                    },
                    isUserMoveToStrip = { SystemClock.uptimeMillis() - lastLeftKeyAt < STRIP_KEY_WINDOW_MS },
                    onStrayStripFocus = {
                        scope.launch {
                            withFrameNanos { }
                            runCatching { catalogFocus.requestFocus() }
                        }
                    }
                )
            }
        }
    }

    menuItem?.let { item ->
        val inList = item.key in favorites
        val inProgress = continueWatching.any { it.key == item.key }
        val actions = buildList {
            add(ContentAction(Icons.Default.PlayArrow, if (inProgress) "Seguir viendo" else "Reproducir") {
                when (item) {
                    is BrowseItem.Movie -> onPlayMovie(item.movie.id)
                    is BrowseItem.Show -> viewModel.playSeries(item.series, onPlayEpisode)
                    is BrowseItem.Live -> onOpen(item)
                }
            })
            if (item !is BrowseItem.Live) {
                add(ContentAction(Icons.Default.Info, "Ver ficha") { onOpen(item) })
            }
            add(
                ContentAction(
                    if (inList) Icons.Default.Check else Icons.Default.Add,
                    if (inList) "Quitar de Mi lista" else "Agregar a Mi lista"
                ) { viewModel.toggleFavorite(item) }
            )
            if (item is BrowseItem.Movie) {
                add(ContentAction(Icons.Default.DoneAll, "Marcar como vista") { viewModel.markWatched(item.movie) })
            }
            if (inProgress) {
                add(ContentAction(Icons.Default.Close, "Quitar de Seguir viendo") { viewModel.removeFromContinue(item) })
            }
            if (item !is BrowseItem.Live) {
                add(
                    ContentAction(
                        Icons.Default.Replay,
                        if (item is BrowseItem.Show) "Reiniciar serie" else "Reiniciar progreso",
                        destructive = true
                    ) { viewModel.resetProgress(item) }
                )
            }
            add(
                ContentAction(
                    Icons.Default.VisibilityOff,
                    if (item is BrowseItem.Live) "Ocultar canal" else "Ocultar título"
                ) { viewModel.hideItem(item) }
            )
        }
        fun group(name: String) = TitleCleaner.clean(name, catalogPrefs.cleanNames).ifBlank { null }
        ContentMenu(
            title = item.title,
            subtitle = when (item) {
                is BrowseItem.Movie -> listOfNotNull("Película", group(item.movie.categoryName), ratingText(item.rating)).joinToString(" · ")
                is BrowseItem.Show -> listOfNotNull("Serie", group(item.series.categoryName), ratingText(item.rating)).joinToString(" · ")
                is BrowseItem.Live -> listOfNotNull("Canal en vivo", group(item.channel.groupTitle)).joinToString(" · ")
            },
            imageUrl = item.imageUrl,
            actions = actions,
            onDismiss = { menuItem = null }
        )
    }

    if (showAccount) {
        val current = account
        if (current != null) {
            AccountDialog(
                initialName = current.name,
                initialUser = current.username,
                initialPass = current.password,
                saving = accountSaving,
                error = accountError,
                onSave = { name, user, pass -> viewModel.updateAccount(name, user, pass) { showAccount = false } },
                onDismiss = {
                    viewModel.clearAccountError()
                    showAccount = false
                }
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Cerrar sesión") },
            text = {
                Text(
                    "Se borrarán de este equipo el catálogo guardado, tu progreso y Mi lista de esta cuenta. " +
                        "Las descargas terminadas se conservan y puedes verlas desde Descargas."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    showSettings = false
                    viewModel.logout()
                }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } }
        )
    }

    confirmAdult?.let { change ->
        AlertDialog(
            onDismissRequest = { confirmAdult = null },
            title = { Text("Marcar como adultos") },
            text = {
                Text(
                    "“${change.label}” se tratará como contenido para adultos: " +
                        (if (pinSet) "quedará oculta hasta que ingreses el PIN." else "crea un PIN en Configuración para bloquearla.") +
                        " Para quitar la marca después se pedirá el PIN."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAdult = null
                    applyChange(change)
                }) { Text("Marcar") }
            },
            dismissButton = { TextButton(onClick = { confirmAdult = null }) { Text("Cancelar") } }
        )
    }

    if (showResetAll) {
        AlertDialog(
            onDismissRequest = { showResetAll = false },
            title = { Text("Borrar todo el progreso") },
            text = { Text("Todas las series volverán al episodio 1 y las películas al minuto 0. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetAllProgress(); showResetAll = false }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetAll = false }) { Text("Cancelar") } }
        )
    }

    categoryMenu?.let { name ->
        val info = organizeList.firstOrNull { it.name == name }
        val adult = info?.adult ?: false
        val label = content.categoryLabels[name] ?: name
        val actions = buildList {
            add(ContentAction(Icons.Default.PlayArrow, "Ver categoría") { openCategory(name) })
            add(ContentAction(Icons.Default.KeyboardArrowUp, "Subir") { viewModel.moveCategory(ui.section, name, -1) })
            add(ContentAction(Icons.Default.KeyboardArrowDown, "Bajar") { viewModel.moveCategory(ui.section, name, 1) })
            add(ContentAction(Icons.Default.VisibilityOff, "Ocultar categoría") { viewModel.setCategoryHidden(ui.section, name, true) })
            add(
                ContentAction(
                    if (adult) Icons.Default.LockOpen else Icons.Default.Lock,
                    if (adult) "Quitar marca de adultos" else "Marcar como adultos"
                ) {
                    requestAdultChange(
                        GuardedChange(
                            ui.section, name, label,
                            if (adult) GuardedChange.Kind.UNMARK_ADULT else GuardedChange.Kind.MARK_ADULT
                        )
                    )
                }
            )
            add(ContentAction(Icons.AutoMirrored.Filled.Sort, "Organizar categorías…") { openOrganize() })
        }
        ContentMenu(
            title = label,
            subtitle = listOfNotNull(
                content.categoryCounts[name]?.let { if (it == 1) "1 título" else "$it títulos" },
                if (adult) "Adultos" else null
            ).joinToString(" · "),
            imageUrl = "",
            actions = actions,
            onDismiss = { categoryMenu = null }
        )
    }

    if (showSettings) {
        val updateViewModel: UpdateViewModel = hiltViewModel()
        SettingsPanel(
            accountName = account?.name.orEmpty(),
            accountUser = account?.username.orEmpty(),
            onEditAccount = {
                viewModel.clearAccountError()
                showAccount = true
            },
            onLogout = { confirmLogout = true },
            appVersion = updateViewModel.versionName,
            onCheckUpdates = updateViewModel::checkNow,
            downloadWifiOnly = downloadWifiOnly,
            onDownloadWifiOnlyChange = viewModel::setDownloadWifiOnly,
            bufferSeconds = bufferSeconds,
            maxBufferSeconds = maxBufferSeconds,
            defaultSort = SortOrder.fromName(catalogPrefs.defaultSort),
            cleanNames = catalogPrefs.cleanNames,
            hiddenCount = hiddenItems.size,
            pinSet = pinSet,
            unlocked = parentalUnlocked,
            onBufferChange = viewModel::setBufferSeconds,
            onMaxBufferChange = viewModel::setMaxBufferSeconds,
            onDefaultSortChange = viewModel::setDefaultSort,
            onCleanNamesChange = viewModel::setCleanNames,
            onOrganize = { openOrganize() },
            onHiddenItems = { showHidden = true },
            onCreatePin = { pinError = null; pinStep = PinStep.Create },
            onChangePin = { pinError = null; pinStep = PinStep.VerifyForChange },
            onRemovePin = { pinError = null; pinStep = PinStep.VerifyForRemove },
            onLockNow = viewModel::lockParental,
            onUnlock = { pinError = null; pinStep = PinStep.Unlock(null) },
            onDismiss = { showSettings = false }
        )
    }

    if (showOrganize) {
        OrganizeCategoriesDialog(
            section = organizingSection,
            categories = organizeList,
            onSection = viewModel::organize,
            onToggleHidden = {
                if (it.hidden && it.adult && pinSet) {
                    requestAdultChange(GuardedChange(organizingSection, it.name, it.label, GuardedChange.Kind.UNHIDE))
                } else viewModel.setCategoryHidden(organizingSection, it.name, !it.hidden)
            },
            onMove = { c, delta -> viewModel.moveCategory(organizingSection, c.name, delta) },
            onToggleAdult = {
                requestAdultChange(
                    GuardedChange(
                        organizingSection, it.name, it.label,
                        if (it.adult) GuardedChange.Kind.UNMARK_ADULT else GuardedChange.Kind.MARK_ADULT
                    )
                )
            },
            onResetOrder = { viewModel.resetCategoryOrder(organizingSection) },
            onDismiss = { showOrganize = false }
        )
    }

    if (showHidden) {
        HiddenItemsDialog(
            items = hiddenItems,
            onUnhide = { viewModel.unhideItem(it.contentKey) },
            onDismiss = { showHidden = false }
        )
    }

    pinStep?.let { step ->
        PinDialog(
            title = when (step) {
                is PinStep.Unlock, is PinStep.Guard -> "Ingresa el PIN"
                PinStep.Create, is PinStep.GuardCreate -> "Nuevo PIN"
                is PinStep.Confirm, is PinStep.GuardConfirm -> "Repite el PIN"
                PinStep.VerifyForChange, PinStep.VerifyForRemove -> "PIN actual"
            },
            message = when (step) {
                is PinStep.Unlock -> "Contenido para adultos. Queda desbloqueado hasta cerrar la app."
                is PinStep.Guard -> when (step.change.kind) {
                    GuardedChange.Kind.UNHIDE -> "Para mostrar “${step.change.label}” (adultos) ingresa el PIN."
                    else -> "Para quitar la marca de adultos de “${step.change.label}” ingresa el PIN."
                }
                is PinStep.GuardCreate -> "Para quitar la marca de adultos primero crea un PIN de 4 dígitos. Lo usarás para el contenido para adultos."
                PinStep.Create -> "Elige 4 dígitos para bloquear las categorías de adultos."
                is PinStep.Confirm, is PinStep.GuardConfirm -> "Ingrésalo otra vez para confirmar."
                PinStep.VerifyForChange -> "Ingresa el PIN actual para cambiarlo."
                PinStep.VerifyForRemove -> "Ingresa el PIN actual para quitarlo."
            },
            error = pinError,
            onSubmit = { pin ->
                when (step) {
                    is PinStep.Unlock -> viewModel.unlockParental(pin) { ok ->
                        if (ok) {
                            pinStep = null
                            pinError = null
                            step.category?.let(viewModel::selectCategory)
                        } else pinError = "PIN incorrecto"
                    }
                    PinStep.Create -> {
                        pinError = null
                        pinStep = PinStep.Confirm(pin)
                    }
                    is PinStep.Confirm -> if (pin == step.first) {
                        viewModel.setPin(pin)
                        pinStep = null
                        pinError = null
                    } else {
                        pinError = "Los PIN no coinciden, inténtalo de nuevo"
                        pinStep = PinStep.Create
                    }
                    is PinStep.Guard -> viewModel.verifyPin(pin) { ok ->
                        if (ok) {
                            pinStep = null
                            pinError = null
                            applyChange(step.change)
                        } else pinError = "PIN incorrecto"
                    }
                    is PinStep.GuardCreate -> {
                        pinError = null
                        pinStep = PinStep.GuardConfirm(step.change, pin)
                    }
                    is PinStep.GuardConfirm -> if (pin == step.first) {
                        viewModel.setPin(pin)
                        pinStep = null
                        pinError = null
                        applyChange(step.change)
                    } else {
                        pinError = "Los PIN no coinciden, inténtalo de nuevo"
                        pinStep = PinStep.GuardCreate(step.change)
                    }
                    PinStep.VerifyForChange -> viewModel.unlockParental(pin) { ok ->
                        if (ok) {
                            pinError = null
                            pinStep = PinStep.Create
                        } else pinError = "PIN incorrecto"
                    }
                    PinStep.VerifyForRemove -> viewModel.clearPin(pin) { ok ->
                        if (ok) {
                            pinStep = null
                            pinError = null
                        } else pinError = "PIN incorrecto"
                    }
                }
            },
            onDismiss = {
                pinStep = null
                pinError = null
            }
        )
    }
}

/** Paso del diálogo de PIN. */
private sealed interface PinStep {
    data class Unlock(val category: String?) : PinStep
    data object Create : PinStep
    data class Confirm(val first: String) : PinStep
    data object VerifyForChange : PinStep
    data object VerifyForRemove : PinStep
    /** Cambio protegido por el PIN (ver [GuardedChange]). */
    data class Guard(val change: GuardedChange) : PinStep
    /** Sin PIN todavía: se crea antes de aplicar el cambio. */
    data class GuardCreate(val change: GuardedChange) : PinStep
    data class GuardConfirm(val change: GuardedChange, val first: String) : PinStep
}

/** Cambio sobre una categoría que toca el control parental. */
private data class GuardedChange(val section: Section, val name: String, val label: String, val kind: Kind) {
    enum class Kind { MARK_ADULT, UNMARK_ADULT, UNHIDE }
}

/**
 * Recuerda qué póster tenía el foco ("fila|clave") para devolvérselo al volver a Inicio.
 * Solo el póster recordado lleva el [requester].
 */
private class FocusMemory(
    val requester: FocusRequester,
    val target: String?,
    val onFocused: (String) -> Unit
) {
    fun modifierFor(key: String): Modifier = if (key == target) Modifier.focusRequester(requester) else Modifier
}

private class FocusHolder(var key: String? = null) {
    companion object {
        val Saver: Saver<FocusHolder, String> = Saver(save = { it.key.orEmpty() }, restore = { FocusHolder(it.ifEmpty { null }) })
    }
}

@Composable
private fun PreparingCatalog() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Preparando tu catálogo…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "La primera vez tarda un poco. Después se abre al instante.",
            style = MaterialTheme.typography.bodyMedium,
            color = EntreBrand.TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ============================ Catálogo en filas ============================

@Composable
private fun CatalogRows(
    profile: DeviceProfile,
    section: Section,
    content: SectionContent,
    continueWatching: List<ContinueItem>,
    emptyText: String,
    stateKey: String,
    bottomSpace: Dp,
    focusMemory: FocusMemory,
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onFocusItem: (BrowseItem) -> Unit,
    onPlayContinue: (ContinueItem) -> Unit,
    onRemoveContinue: (ContinueItem) -> Unit,
    onSeeAll: (ContentRow) -> Unit,
    modifier: Modifier
) {
    // Guardado: Inicio se recrea al volver de la ficha y debe quedar donde estaba.
    val listState = rememberSaveable(stateKey, saver = LazyListState.Saver) { LazyListState() }
    val myList = content.rows.firstOrNull { it.title == MY_LIST_ROW && !content.isSearch }
    val others = content.rows.filter { it !== myList }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(profile.rowGap)
    ) {
        if (myList != null) {
            item(key = "row:${myList.title}", contentType = "row") {
                CatalogRow(profile, myList, focusMemory, onClick, onLongClick, onFocusItem, onSeeAll = { onSeeAll(myList) })
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue", contentType = "continue") {
                ContinueWatchingRow(
                    profile = profile,
                    items = continueWatching,
                    focusMemory = focusMemory,
                    onPlay = onPlayContinue,
                    onRemove = onRemoveContinue
                )
            }
        }
        if (content.top10.isNotEmpty()) {
            item(key = "top10", contentType = "top10") {
                Top10Row(
                    profile = profile,
                    title = content.top10Label,
                    items = content.top10,
                    focusMemory = focusMemory,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onFocusItem = onFocusItem
                )
            }
        }
        others.forEach { row ->
            if (row.items.isNotEmpty()) {
                item(key = "row:${row.title}", contentType = "row") {
                    CatalogRow(profile, row, focusMemory, onClick, onLongClick, onFocusItem, onSeeAll = { onSeeAll(row) })
                }
            }
        }
        if (content.rows.all { it.items.isEmpty() } && continueWatching.isEmpty()) {
            item(key = "empty") {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = profile.edge, vertical = 24.dp)
                )
            }
        }
    }
}

// ============================ Cuadrícula "Ver todo" ============================

@Composable
private fun CatalogGrid(
    profile: DeviceProfile,
    content: SectionContent,
    title: String,
    emptyText: String,
    showBack: Boolean,
    onBack: () -> Unit,
    stateKey: String,
    sort: SortOrder?,
    onSort: (SortOrder) -> Unit,
    bottomSpace: Dp,
    focusMemory: FocusMemory,
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onFocusItem: (BrowseItem) -> Unit,
    modifier: Modifier
) {
    val gridState = rememberSaveable(stateKey, sort, saver = LazyGridState.Saver) { LazyGridState() }
    val total = content.rows.sumOf { it.items.size }
    val multiSection = content.rows.count { it.items.isNotEmpty() } > 1
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = profile.posterWidth),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(start = profile.edge, end = profile.edge, top = 4.dp, bottom = bottomSpace),
        horizontalArrangement = Arrangement.spacedBy(profile.itemGap),
        verticalArrangement = Arrangement.spacedBy(profile.itemGap)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a todas")
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = if (profile.isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (total == 1) "1 título" else "$total títulos",
                        style = MaterialTheme.typography.labelMedium,
                        color = EntreBrand.TextDim
                    )
                }
            }
        }
        if (content.sortOptions.size > 1 && total > 1) {
            item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = EntreBrand.TextDim, modifier = Modifier.size(18.dp))
                    content.sortOptions.forEach { order ->
                        FilterChip(
                            selected = sort == order,
                            onClick = { onSort(order) },
                            label = { Text(order.label) },
                            leadingIcon = if (sort == order) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        }
        if (total == 0) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EntreBrand.TextDim,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        content.rows.forEach { row ->
            if (row.items.isEmpty()) return@forEach
            if (multiSection) {
                item(key = "h:${row.title}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "${row.label} · ${row.items.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            items(
                count = row.items.size,
                key = { index -> "${row.title}:${row.items[index].key}" },
                contentType = { index -> if (row.items[index] is BrowseItem.Live) "live" else "poster" }
            ) { index ->
                val item = row.items[index]
                val memoryKey = "grid|${row.title}|${item.key}"
                PosterCard(
                    profile = profile,
                    item = item,
                    fillWidth = true,
                    modifier = focusMemory.modifierFor(memoryKey),
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onFocused = {
                        focusMemory.onFocused(memoryKey)
                        onFocusItem(item)
                    }
                )
            }
        }
    }
}

// ============================ Top 10 ============================

/** Fila "Top 10" estilo Netflix: número grande con contorno y el póster encima. */
@Composable
private fun Top10Row(
    profile: DeviceProfile,
    title: String,
    items: List<BrowseItem>,
    focusMemory: FocusMemory,
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onFocusItem: (BrowseItem) -> Unit
) {
    Column {
        RowHeader(title, profile, onSeeAll = null)
        LazyRow(
            contentPadding = PaddingValues(horizontal = profile.edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(profile.itemGap / 2)
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                val memoryKey = "top10|${item.key}"
                Top10Card(
                    profile = profile,
                    rank = index + 1,
                    item = item,
                    modifier = focusMemory.modifierFor(memoryKey),
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onFocused = {
                        focusMemory.onFocused(memoryKey)
                        onFocusItem(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun Top10Card(
    profile: DeviceProfile,
    rank: Int,
    item: BrowseItem,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit
) {
    val density = LocalDensity.current
    val numberWidth = profile.posterWidth * (if (rank >= 10) 0.95f else 0.6f)
    val fontSize = with(density) { (profile.posterHeight * 0.78f).toSp() }
    Box(Modifier.width(numberWidth + profile.posterWidth * 0.82f)) {
        Text(
            rank.toString(),
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            letterSpacing = (-6).sp,
            softWrap = false,
            maxLines = 1,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.85f),
                drawStyle = Stroke(width = with(density) { 3.dp.toPx() }),
                lineHeight = fontSize
            ),
            modifier = Modifier.align(Alignment.BottomStart)
        )
        PosterCard(
            profile = profile,
            item = item,
            fillWidth = false,
            modifier = modifier.align(Alignment.TopEnd),
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused
        )
    }
}

// ============================ Cabecera ============================

@Composable
private fun HomeHeader(
    profile: DeviceProfile,
    refreshing: Boolean,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit,
    onClearImages: () -> Unit,
    onResetAll: () -> Unit,
    onSettings: () -> Unit,
    onDownloads: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = profile.edge, end = profile.edge / 2, top = 6.dp, bottom = 2.dp)
    ) {
        EntreWordmark(fontSize = if (profile.isTv) 30.sp else 24.sp)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleSearch) {
            Icon(
                if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (searchOpen) "Cerrar búsqueda" else "Buscar"
            )
        }
        IconButton(onClick = onRefresh) {
            if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Descargas") },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                    onClick = { menuOpen = false; onDownloads() }
                )
                DropdownMenuItem(
                    text = { Text("Configuración") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { menuOpen = false; onSettings() }
                )
                DropdownMenuItem(
                    text = { Text("Borrar caché de imágenes y miniaturas") },
                    onClick = { menuOpen = false; onClearImages() }
                )
                DropdownMenuItem(
                    text = { Text("Borrar todo el progreso (series y películas)") },
                    onClick = { menuOpen = false; onResetAll() }
                )
            }
        }
    }
}

// ============================ Seguir viendo ============================

@Composable
private fun ContinueWatchingRow(
    profile: DeviceProfile,
    items: List<ContinueItem>,
    focusMemory: FocusMemory,
    onPlay: (ContinueItem) -> Unit,
    onRemove: (ContinueItem) -> Unit
) {
    Column {
        RowHeader("Seguir viendo", profile, onSeeAll = null)
        LazyRow(
            contentPadding = PaddingValues(horizontal = profile.edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(profile.itemGap)
        ) {
            items(items, key = { it.key }) { item ->
                val memoryKey = "continue|${item.key}"
                ContinueCard(
                    profile = profile,
                    item = item,
                    modifier = focusMemory.modifierFor(memoryKey),
                    onFocused = { focusMemory.onFocused(memoryKey) },
                    onPlay = { onPlay(item) },
                    onRemove = { onRemove(item) }
                )
            }
        }
    }
}

private fun remainingLabel(ms: Long): String {
    val minutes = ((ms + 59_999) / 60_000).toInt()
    return when {
        minutes <= 1 -> "queda 1 min"
        minutes < 60 -> "quedan $minutes min"
        else -> "quedan ${minutes / 60} h ${minutes % 60} min"
    }
}

@Composable
private fun ContinueCard(
    profile: DeviceProfile,
    item: ContinueItem,
    modifier: Modifier,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "continueScale")
    LaunchedEffect(focused) { if (focused) onFocused() }

    Card(
        onClick = onPlay,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EntreBrand.Panel),
        border = if (focused) BorderStroke(3.dp, EntreBrand.Accent) else null,
        interactionSource = source,
        modifier = modifier
            .width(profile.continueWidth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(profile.continueHeight)
                .background(EntreBrand.PanelHigh),
            contentAlignment = Alignment.Center
        ) {
            FallbackImage(item.images, contentDescription = item.title, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
            )
            Surface(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(50)) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Continuar",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (item.fraction > 0f) {
                Text(
                    listOfNotNull(
                        "${(item.fraction * 100).toInt()} %".takeIf { item.fraction >= 0.01f },
                        item.remainingMs.takeIf { it > 0 }?.let(::remainingLabel)
                    ).joinToString(" · "),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp)
                )
                LinearProgressIndicator(
                    progress = { item.fraction },
                    color = EntreBrand.Accent,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
        Column(Modifier.padding(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotEmpty()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================ Filas del catálogo ============================

@Composable
private fun CatalogRow(
    profile: DeviceProfile,
    row: ContentRow,
    focusMemory: FocusMemory,
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onFocusItem: (BrowseItem) -> Unit,
    onSeeAll: () -> Unit
) {
    Column {
        RowHeader(row.label, profile, onSeeAll = onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = profile.edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(profile.itemGap)
        ) {
            items(row.items, key = { it.key }, contentType = { if (it is BrowseItem.Live) "live" else "poster" }) { item ->
                val memoryKey = "${row.title}|${item.key}"
                PosterCard(
                    profile = profile,
                    item = item,
                    fillWidth = false,
                    modifier = focusMemory.modifierFor(memoryKey),
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onFocused = {
                        focusMemory.onFocused(memoryKey)
                        onFocusItem(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun RowHeader(title: String, profile: DeviceProfile, onSeeAll: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = profile.edge, end = profile.edge - 8.dp, top = 8.dp)
    ) {
        Text(
            text = title,
            style = if (profile.isTv) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            SeeAllButton(onSeeAll)
        }
    }
}

@Composable
private fun SeeAllButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(if (focused) EntreBrand.Accent else Color.Transparent)
            .border(1.dp, EntreBrand.Accent.copy(alpha = if (focused) 0f else 0.5f), shape)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
    ) {
        Text(
            "Ver todo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) EntreBrand.OnAccent else EntreBrand.Accent
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (focused) EntreBrand.OnAccent else EntreBrand.Accent,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    profile: DeviceProfile,
    item: BrowseItem,
    fillWidth: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "posterScale")
    LaunchedEffect(focused) { if (focused) onFocused() }
    val isLive = item is BrowseItem.Live
    val shape = RoundedCornerShape(12.dp)

    val outer = if (fillWidth) Modifier.fillMaxWidth()
    else Modifier.width(if (isLive) profile.liveWidth else profile.posterWidth)
    val imageBox = when {
        fillWidth && isLive -> Modifier.fillMaxWidth().aspectRatio(16f / 10f)
        fillWidth -> Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        else -> Modifier.fillMaxWidth().height(if (isLive) profile.liveHeight else profile.posterHeight)
    }

    Column(
        modifier = modifier
            .then(outer)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick
            )
    ) {
        Box(
            imageBox
                .clip(shape)
                .background(
                    if (isLive) Brush.verticalGradient(listOf(EntreBrand.PanelHigh, EntreBrand.Panel))
                    else Brush.verticalGradient(listOf(EntreBrand.PanelHigh, EntreBrand.PanelHigh))
                )
                .then(if (focused) Modifier.border(3.dp, EntreBrand.Accent, shape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = if (isLive) Modifier.fillMaxSize().padding(14.dp) else Modifier.fillMaxSize(),
                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop
                )
            } else {
                Icon(iconFor(item), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ratingText(item.rating)?.let { rating ->
                Text(
                    rating,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isLive) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )
    }
}

private fun ratingText(rating: Float): String? =
    if (rating > 0f) "★ " + "%.1f".format(rating) else null

private fun iconFor(item: BrowseItem): ImageVector = when (item) {
    is BrowseItem.Live -> Icons.Default.Tv
    is BrowseItem.Movie -> Icons.Default.Movie
    is BrowseItem.Show -> Icons.Default.Tv
}

