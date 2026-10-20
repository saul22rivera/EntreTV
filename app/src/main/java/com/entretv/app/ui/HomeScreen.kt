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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.home.ContentAction
import com.entretv.app.ui.home.ContentMenu
import com.entretv.app.ui.home.HiddenItemsDialog
import com.entretv.app.ui.home.NavRail
import com.entretv.app.ui.home.OrganizeCategoriesDialog
import com.entretv.app.ui.home.PinDialog
import com.entretv.app.ui.home.SettingsPanel
import com.entretv.app.ui.home.PILL_SPACE
import com.entretv.app.ui.home.RAIL_COLLAPSED_WIDTH
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreWordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Si la tira de íconos recibe el foco dentro de este tiempo tras una flecha izquierda, la llevó el usuario. */
private const val STRIP_KEY_WINDOW_MS = 700L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun HomeScreen(
    onOpen: (BrowseItem) -> Unit,
    onPlayMovie: (String) -> Unit,
    onPlayProgress: (PlaybackProgressEntity) -> Unit,
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
    val backdrops by viewModel.backdrops.collectAsState()
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
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(railExpanded, contentStale) {
        if (!railExpanded && !contentStale && profile.isTv) {
            withFrameNanos { }
            if (!catalogHasFocus) runCatching { catalogFocus.requestFocus() }
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

    // Atrás primero cierra la búsqueda o la cuadrícula, después sale de la app.
    BackHandler(enabled = searchOpen || ui.category != null) {
        when {
            searchOpen -> {
                viewModel.onQueryChange("")
                searchOpen = false
            }
            else -> viewModel.selectCategory(null)
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
                    onAddPlaylist = { showAddDialog = true },
                    onClearImages = {
                        coil.Coil.imageLoader(context).memoryCache?.clear()
                        coil.Coil.imageLoader(context).diskCache?.clear()
                        scope.launch { snackbar.showSnackbar("Caché de imágenes borrado") }
                    },
                    onResetAll = { showResetAll = true },
                    onSettings = { showSettings = true },
                    onDownloads = onOpenDownloads
                )

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
                    when {
                        ui.loading && ui.playlists.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        ui.playlists.isEmpty() -> EmptyState(onAdd = { showAddDialog = true })
                        // Mientras se calcula la nueva sección no se muestran las filas de la anterior:
                        // el foco caería en un póster que desaparece y las flechas dejarían de responder.
                        contentStale ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        content.grid -> CatalogGrid(
                            profile = profile,
                            content = content,
                            title = when {
                                searching -> "Resultados para “${ui.query.trim()}”"
                                ui.section == Section.FAVORITES -> "Mi lista"
                                else -> ui.category?.let { content.categoryLabels[it] ?: it }.orEmpty()
                            },
                            emptyText = when {
                                searching -> "No encontramos nada con “${ui.query.trim()}”"
                                ui.section == Section.FAVORITES -> "Tu lista está vacía. Mantén presionado un póster o usa “Mi lista” en la ficha para guardarlo aquí."
                                else -> "Esta categoría está vacía"
                            },
                            showBack = ui.category != null && !searching,
                            onBack = { viewModel.selectCategory(null) },
                            stateKey = "${ui.section}|${ui.category}|${ui.query}",
                            sort = ui.sort,
                            onSort = viewModel::selectSort,
                            bottomSpace = bottomSpace,
                            onClick = onItemClick,
                            onLongClick = onItemLongClick,
                            onFocusItem = { focusedItem = it },
                            modifier = catalogModifier
                        )
                        else -> CatalogRows(
                            profile = profile,
                            section = ui.section,
                            content = content,
                            continueWatching = continueWatching,
                            favorites = favorites,
                            backdrops = backdrops,
                            bottomSpace = bottomSpace,
                            onClick = onItemClick,
                            onLongClick = onItemLongClick,
                            onFocusItem = { focusedItem = it },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onPlayMovie = onPlayMovie,
                            onPlayProgress = onPlayProgress,
                            onRemoveProgress = viewModel::removeFromContinue,
                            onSeeAll = { row ->
                                if (row.title == MY_LIST_ROW) viewModel.selectSection(Section.FAVORITES)
                                else openCategory(row.title)
                            },
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
                    categories = if (searching || ui.section == Section.FAVORITES) emptyList() else content.categories,
                    categoryCounts = content.categoryCounts,
                    categoryLabels = content.categoryLabels,
                    lockedCategories = content.lockedCategories,
                    selectedCategory = ui.category,
                    showCategories = !searching && ui.section != Section.FAVORITES,
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
        val inProgress = continueWatching.any { it.contentKey == item.key || it.contentKey.startsWith("${item.key}#") }
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

    if (showAddDialog) {
        AddPlaylistDialog(
            loading = ui.loading,
            onDismiss = { showAddDialog = false },
            onAddM3u = { name, url -> viewModel.addM3u(name, url); showAddDialog = false },
            onAddXtream = { name, base, user, pass ->
                viewModel.addXtream(name, base, user, pass); showAddDialog = false
            }
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
                ) { viewModel.setCategoryAdult(ui.section, name, !adult) }
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
            onToggleHidden = { viewModel.setCategoryHidden(organizingSection, it.name, !it.hidden) },
            onMove = { c, delta -> viewModel.moveCategory(organizingSection, c.name, delta) },
            onToggleAdult = { viewModel.setCategoryAdult(organizingSection, it.name, !it.adult) },
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
                is PinStep.Unlock -> "Ingresá el PIN"
                PinStep.Create -> "Nuevo PIN"
                is PinStep.Confirm -> "Repetí el PIN"
                PinStep.VerifyForChange, PinStep.VerifyForRemove -> "PIN actual"
            },
            message = when (step) {
                is PinStep.Unlock -> "Contenido para adultos. Queda desbloqueado hasta cerrar la app."
                PinStep.Create -> "Elegí 4 dígitos para bloquear las categorías de adultos."
                is PinStep.Confirm -> "Ingresalo otra vez para confirmar."
                PinStep.VerifyForChange -> "Ingresá el PIN actual para cambiarlo."
                PinStep.VerifyForRemove -> "Ingresá el PIN actual para quitarlo."
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
                        pinError = "Los PIN no coinciden, probá de nuevo"
                        pinStep = PinStep.Create
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
}

// ============================ Catálogo en filas ============================

@Composable
private fun CatalogRows(
    profile: DeviceProfile,
    section: Section,
    content: SectionContent,
    continueWatching: List<PlaybackProgressEntity>,
    favorites: Set<String>,
    backdrops: Map<String, String>,
    bottomSpace: Dp,
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onFocusItem: (BrowseItem) -> Unit,
    onToggleFavorite: (BrowseItem) -> Unit,
    onPlayMovie: (String) -> Unit,
    onPlayProgress: (PlaybackProgressEntity) -> Unit,
    onRemoveProgress: (String) -> Unit,
    onSeeAll: (ContentRow) -> Unit,
    modifier: Modifier
) {
    val listState = remember(section) { androidx.compose.foundation.lazy.LazyListState() }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(profile.rowGap)
    ) {
        if (content.featured.isNotEmpty()) {
            item(key = "hero", contentType = "hero") {
                FeaturedHero(
                    profile = profile,
                    items = content.featured,
                    backdrops = backdrops,
                    favorites = favorites,
                    onPlay = { item ->
                        when (item) {
                            is BrowseItem.Movie -> onPlayMovie(item.movie.id)
                            else -> onClick(item)
                        }
                    },
                    onInfo = onClick,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue", contentType = "continue") {
                ContinueWatchingRow(
                    profile = profile,
                    items = continueWatching,
                    onPlay = onPlayProgress,
                    onRemove = onRemoveProgress
                )
            }
        }
        content.rows.forEach { row ->
            if (row.items.isNotEmpty()) {
                item(key = "row:${row.title}", contentType = "row") {
                    CatalogRow(
                        profile = profile,
                        row = row,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onFocusItem = onFocusItem,
                        onSeeAll = { onSeeAll(row) }
                    )
                }
            }
        }
        if (content.rows.all { it.items.isEmpty() } && continueWatching.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Todavía no hay contenido en ${section.label}",
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
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onFocusItem: (BrowseItem) -> Unit,
    modifier: Modifier
) {
    val gridState = remember(stateKey, sort) { androidx.compose.foundation.lazy.grid.LazyGridState() }
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
                PosterCard(
                    profile = profile,
                    item = item,
                    fillWidth = true,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onFocused = { onFocusItem(item) }
                )
            }
        }
    }
}

// ============================ Banner destacado ============================

@Composable
private fun FeaturedHero(
    profile: DeviceProfile,
    items: List<BrowseItem>,
    backdrops: Map<String, String>,
    favorites: Set<String>,
    onPlay: (BrowseItem) -> Unit,
    onInfo: (BrowseItem) -> Unit,
    onToggleFavorite: (BrowseItem) -> Unit
) {
    var index by rememberSaveable(items.size) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val current = items[index.coerceIn(0, items.lastIndex)]
    val wide = !profile.compactRail
    val height: Dp = when {
        profile.isTv -> 380.dp
        wide -> 340.dp
        else -> 400.dp
    }

    // Rota cada 8 s; se queda quieto mientras el usuario tiene el foco encima.
    LaunchedEffect(items, focused) {
        if (focused || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(8_000)
            index = (index + 1) % items.size
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = profile.edge)
            .clip(RoundedCornerShape(18.dp))
            .background(EntreBrand.Panel)
            .onFocusChanged { focused = it.hasFocus }
    ) {
        Crossfade(targetState = current, animationSpec = tween(700), label = "hero") { item ->
            val backdrop = backdrops[item.key]
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = backdrop ?: item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = if (backdrop == null) Alignment.TopCenter else Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (backdrop == null && wide) 0.45f else 1f }
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to EntreBrand.Ink.copy(alpha = 0.96f)
                            )
                        )
                )
                if (wide) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0f to EntreBrand.Ink.copy(alpha = 0.92f),
                                    0.6f to Color.Transparent
                                )
                            )
                    )
                    // Sin fondo panorámico, el póster nítido a la derecha.
                    if (backdrop == null && item.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(20.dp)
                                .fillMaxHeight()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            modifier = Modifier
                .align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .widthIn(max = 560.dp)
        ) {
            Text(
                "DESTACADO",
                color = EntreBrand.Accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                current.title,
                color = Color.White,
                style = if (profile.isTv) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EntreButton(
                    text = "Reproducir",
                    icon = Icons.Default.PlayArrow,
                    primary = true,
                    compact = !wide,
                    onClick = { onPlay(current) }
                )
                EntreButton(
                    text = "Más info",
                    icon = Icons.Default.Info,
                    compact = !wide,
                    onClick = { onInfo(current) }
                )
                val inList = current.key in favorites
                EntreButton(
                    text = if (wide) (if (inList) "En Mi lista" else "Mi lista") else "",
                    icon = if (inList) Icons.Default.Check else Icons.Default.Add,
                    compact = !wide,
                    onClick = { onToggleFavorite(current) }
                )
            }
            if (items.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.indices.forEach { i ->
                        Box(
                            Modifier
                                .height(4.dp)
                                .width(if (i == index) 20.dp else 8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i == index) EntreBrand.Accent else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }
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
    onAddPlaylist: () -> Unit,
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
                    text = { Text("Agregar lista") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { menuOpen = false; onAddPlaylist() }
                )
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
    items: List<PlaybackProgressEntity>,
    onPlay: (PlaybackProgressEntity) -> Unit,
    onRemove: (String) -> Unit
) {
    Column {
        RowHeader("Seguir viendo", profile, onSeeAll = null)
        LazyRow(
            contentPadding = PaddingValues(horizontal = profile.edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(profile.itemGap)
        ) {
            items(items, key = { it.contentKey }) { progress ->
                ContinueCard(
                    profile = profile,
                    progress = progress,
                    onPlay = { onPlay(progress) },
                    onRemove = { onRemove(progress.contentKey) }
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    profile: DeviceProfile,
    progress: PlaybackProgressEntity,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val percent = if (progress.durationMs > 0)
        (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "continueScale")

    Card(
        onClick = onPlay,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EntreBrand.Panel),
        border = if (focused) BorderStroke(3.dp, EntreBrand.Accent) else null,
        interactionSource = source,
        modifier = Modifier
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
            if (progress.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = progress.imageUrl,
                    contentDescription = progress.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
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
            LinearProgressIndicator(
                progress = { percent },
                color = EntreBrand.Accent,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
        Column(Modifier.padding(8.dp)) {
            Text(progress.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress.subtitle.isNotEmpty()) {
                Text(
                    progress.subtitle,
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
                PosterCard(
                    profile = profile,
                    item = item,
                    fillWidth = false,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onFocused = { onFocusItem(item) }
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
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(
            "Ver todo",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) EntreBrand.OnAccent else EntreBrand.Accent
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (focused) EntreBrand.OnAccent else EntreBrand.Accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    profile: DeviceProfile,
    item: BrowseItem,
    fillWidth: Boolean,
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
        modifier = outer
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

// ============================ Diálogos ============================

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EntreWordmark(fontSize = 40.sp)
        Spacer(Modifier.height(16.dp))
        Text("Bienvenido a EntreTV", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Agrega una lista M3U o una cuenta Xtream para empezar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) { Text("Agregar mi primera lista") }
    }
}

@Composable
private fun AddPlaylistDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onAddM3u: (String, String) -> Unit,
    onAddXtream: (String, String, String, String) -> Unit
) {
    var isXtream by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(if (isXtream) "Cuenta Xtream" else "Lista M3U") },
        text = {
            Column {
                FilterChip(
                    selected = isXtream,
                    onClick = { isXtream = !isXtream },
                    label = { Text(if (isXtream) "Usar lista M3U" else "Usar cuenta Xtream") },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(if (isXtream) "URL del servidor" else "URL de la lista M3U") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (isXtream) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Contraseña") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && name.isNotBlank() && url.isNotBlank() && (!isXtream || (user.isNotBlank() && pass.isNotBlank())),
                onClick = {
                    if (isXtream) onAddXtream(name.trim(), url.trim(), user.trim(), pass.trim())
                    else onAddM3u(name.trim(), url.trim())
                }
            ) { Text(if (loading) "Agregando…" else "Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } }
    )
}
