package com.entretv.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarViewDay
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.entretv.app.R
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.home.ContentAction
import com.entretv.app.ui.home.ContentMenu
import com.entretv.app.ui.home.AccountDialog
import com.entretv.app.ui.home.HiddenItemsDialog
import com.entretv.app.ui.home.NavRail
import com.entretv.app.ui.home.CategoryChips
import com.entretv.app.ui.home.TvCatalogNav
import com.entretv.app.ui.home.TvCategoryColumn
import com.entretv.app.ui.home.TvLiveScreen
import com.entretv.app.ui.home.tvNavItem
import com.entretv.app.ui.home.OrganizeCategoriesDialog
import com.entretv.app.ui.home.PinDialog
import com.entretv.app.ui.home.SettingsPanel
import com.entretv.app.ui.home.PILL_SPACE
import com.entretv.app.ui.home.RAIL_COLLAPSED_WIDTH
import com.entretv.app.ui.home.SortMenu
import com.entretv.app.ui.profile.ProfileActions
import com.entretv.app.ui.profile.ProfileMenu
import com.entretv.app.ui.profile.profileColor
import com.entretv.app.ui.profile.profileDisplayName
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreWordmark
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Si la tira de íconos recibe el foco dentro de este tiempo tras una flecha izquierda, la llevó el usuario. */
private const val STRIP_KEY_WINDOW_MS = 700L

/** Separación mínima entre repeticiones de una flecha mantenida en TV. */
private const val KEY_REPEAT_GAP_MS = 70L
private const val KEY_REPEAT_GAP_LOW_RAM_MS = 110L
private val DPAD_KEYS = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/** Pósters de la fila siguiente que se arman (y bajan) antes de llegar a ella. */
private const val ROW_PREFETCH = 8
private const val ROW_PREFETCH_LOW_RAM = 4
private val DeviceProfile.rowPrefetch get() = if (lowRam) ROW_PREFETCH_LOW_RAM else ROW_PREFETCH

private val LIVE_CARD_BRUSH = Brush.verticalGradient(listOf(EntreBrand.PanelHigh, EntreBrand.Panel))
private val POSTER_WAIT_BRUSH = Brush.verticalGradient(listOf(EntreBrand.PanelHigh, EntreBrand.Accent.copy(alpha = 0.16f)))

/**
 * Estado de una cuadrícula de pósters que compone las filas antes de que entren en pantalla
 * (una pantalla por delante y media por detrás; con poca memoria, media por delante): sus
 * pósters ya se están pidiendo al bajar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberPosterGridState(profile: DeviceProfile, vararg keys: Any?): LazyGridState {
    val window = remember(profile.lowRam) {
        if (profile.lowRam) LazyLayoutCacheWindow(aheadFraction = 0.5f)
        else LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f)
    }
    val saver = remember(window) {
        listSaver<LazyGridState, Int>(
            save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
            restore = { LazyGridState(window, it[0], it[1]) }
        )
    }
    return rememberSaveable(*keys, saver = saver) { LazyGridState(window) }
}

/** Mientras llega el póster, o si falla: el título sobre un degradado en vez de un cuadro oscuro. */
@Composable
private fun PosterPlaceholder(title: String) {
    Box(
        Modifier.fillMaxSize().background(POSTER_WAIT_BRUSH).padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = EntreBrand.TextDim,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun HomeScreen(
    onOpen: (BrowseItem) -> Unit,
    onPlayMovie: (String) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    /** TV En vivo: pantalla completa del canal que ya suena en el mini reproductor. */
    onPlayLiveFullscreen: (String) -> Unit = {},
    /** TV En vivo: guía en cuadrícula (lista, categoría elegida). */
    onOpenGuide: (String, String?) -> Unit = { _, _ -> },
    profileMenu: ProfileMenu? = null,
    profileActions: ProfileActions? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()
    val ui by viewModel.uiState.collectAsState()
    val content by viewModel.content.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    // Se pasa el estado y no el valor: cambiar un favorito solo redibuja el póster que cambió.
    val favorites = viewModel.favoriteKeySet.collectAsState()
    val account by viewModel.account.collectAsState()
    val accountSaving by viewModel.accountSaving.collectAsState()
    val accountError by viewModel.accountError.collectAsState()
    val bufferSeconds by viewModel.bufferSeconds.collectAsState()
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsState()
    val maxBufferSeconds by viewModel.maxBufferSeconds.collectAsState()
    val catalogPrefs by viewModel.catalogPrefs.collectAsState()
    val playerPrefs by viewModel.playerPrefs.collectAsState()
    val showClock by viewModel.showClock.collectAsState()
    val accountInfo by viewModel.accountInfo.collectAsState()
    val lastRefreshAt by viewModel.lastRefreshAt.collectAsState()
    val refreshMode by viewModel.refreshMode.collectAsState()
    val listViewSections by viewModel.listViewSections.collectAsState()
    val pinSet by viewModel.pinSet.collectAsState()
    val parentalUnlocked by viewModel.parentalUnlocked.collectAsState()
    val hiddenItems by viewModel.hiddenItems.collectAsState()
    val organizeList by viewModel.organizeList.collectAsState()
    val organizingSection by viewModel.organizingSection.collectAsState()
    // Si la app quedó abierta desde ayer, al volver a ella después del corte también se actualiza.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshIfDue() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.catalogPostersLoop() }
            viewModel.autoRefreshLoop()
        }
    }
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
    // Celular: al abrir la app se ve el resumen hasta elegir una sección o categoría. En TV la
    // app abre en su sección de siempre y al resumen se entra con Inicio del menú.
    var tvHomeChosen by rememberSaveable { mutableStateOf(false) }
    val showSummary = (profile.compactRail || (profile.isTv && tvHomeChosen)) && ui.summary && !searching
    // TV: En vivo es la vista de categorías, canales y mini reproductor.
    val tvLive = profile.isTv && ui.section == Section.LIVE && !searching && ui.category == null && !showSummary
    var liveCategory by remember { mutableStateOf<String?>(null) }
    // "Ver todo" de Seguir viendo: vale para la vista donde se abrió (resumen o sección).
    val continueContext = if (showSummary) "summary" else ui.section.name
    var continueAllIn by rememberSaveable { mutableStateOf<String?>(null) }
    val showContinueAll = continueAllIn == continueContext && !searching && ui.category == null
    LaunchedEffect(continueContext, searching, ui.category) {
        if (continueAllIn != null && !showContinueAll) continueAllIn = null
    }
    val openGuide: (() -> Unit)? = ui.selectedPlaylistId?.takeIf { tvLive }?.let { id -> { onOpenGuide(id, liveCategory) } }
    // La vista en lista o cuadrícula se recuerda por sección; los resultados de búsqueda usan la de su tipo.
    val gridSection = when (ui.category) {
        SEARCH_MOVIES -> Section.MOVIES
        SEARCH_SERIES -> Section.SERIES
        SEARCH_LIVE -> Section.LIVE
        else -> ui.section
    }
    var railExpanded by rememberSaveable { mutableStateOf(false) }
    // Se abrió el menú de TV: al cerrarlo el foco vuelve un paso (ver el efecto de foco de abajo).
    var railWasOpen by remember { mutableStateOf(false) }
    val pillVisible = rememberSaveable { mutableStateOf(true) }
    val catalogFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val headerFocus = remember { FocusRequester() }
    val tvNav = remember(profile.isTv) { if (profile.isTv) TvCatalogNav() else null }
    var catalogHasFocus by remember { mutableStateOf(false) }
    // Pedir el foco de un póster desde fuera del catálogo pasa por su `enter`: si este lo
    // cancelara, la petición se repetiría sin fin y el foco nunca llegaría.
    val catalogFocusFromCode = remember { AtomicBoolean(false) }
    fun focusInCatalog(requester: FocusRequester): Boolean {
        catalogFocusFromCode.set(true)
        return try {
            runCatching { requester.requestFocus() }.isSuccess
        } finally {
            catalogFocusFromCode.set(false)
        }
    }
    // En vivo decide adónde va el foco (categoría, canal o mini reproductor): se le avisa.
    var liveFocusRequests by remember { mutableIntStateOf(0) }
    // Hora de la última flecha izquierda: distingue al usuario que va al menú del foco que
    // el sistema reubica solo al cerrarse el panel.
    var lastLeftKeyAt by remember { mutableStateOf(0L) }
    var menuItem by remember { mutableStateOf<BrowseItem?>(null) }
    // Menú abierto desde "Seguir viendo": tiene sus propias opciones.
    var menuContinue by remember { mutableStateOf<ContinueItem?>(null) }
    var focusedItem by remember { mutableStateOf<BrowseItem?>(null) }
    var focusedContinue by remember { mutableStateOf<ContinueItem?>(null) }
    val onFocusItem: (BrowseItem) -> Unit = {
        focusedItem = it
        focusedContinue = null
    }
    val onFocusContinue: (ContinueItem) -> Unit = {
        focusedContinue = it
        focusedItem = null
    }
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
        if (value) railWasOpen = true
        railExpanded = value
    }

    // Celular: deslizar a la derecha abre las categorías cuando ya no hay nada más a la
    // izquierda. Las filas se mueven con el dedo: solo cuenta lo que la fila ya no pudo mover.
    val openCategoriesOnSwipe = profile.compactRail && !showSummary && !searchOpen && !railExpanded && ui.playlists.isNotEmpty()
    val swipeOpenPx = with(LocalDensity.current) { 56.dp.toPx() }
    val swipeToCategories = remember(swipeOpenPx) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || consumed.x != 0f || available.x <= 0f) {
                    pulled = 0f
                    return Offset.Zero
                }
                pulled += available.x
                if (pulled > swipeOpenPx) {
                    pulled = Float.NEGATIVE_INFINITY
                    setRailExpanded(true)
                }
                return Offset(available.x, 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    // Una vista corta no se puede deslizar para traer la píldora de vuelta.
    LaunchedEffect(ui.section, ui.category, ui.summary) {
        pillVisible.value = true
        tvNav?.reset()
    }

    val contentStale = content.playlistId != ui.selectedPlaylistId ||
        content.section != ui.section || content.category != ui.category

    // TV, Películas y Series: categorías en columna a la izquierda, como En vivo.
    val tvCategoryColumn = profile.isTv && !searching && !showSummary && ui.section != Section.LIVE && ui.playlists.isNotEmpty()
    var tvCatsHaveFocus by remember { mutableStateOf(false) }
    var tvCatsFocusRequests by remember { mutableIntStateOf(0) }
    // OK sobre una categoría que todavía se calcula: el foco pasa al contenido cuando esté listo.
    var contentFocusPending by remember { mutableStateOf(false) }

    // En TV el foco vuelve a los pósters al abrir la app, al cerrar el panel y al cambiar de
    // sección. Pedirlo dentro del cambio de foco que cierra el panel deja el árbol de foco a
    // medias (el póster queda marcado pero las flechas no responden): se espera un frame.
    // Al volver de otra pantalla además se espera a que termine la transición (RESUMED): al
    // quitarse la pantalla anterior Android le devuelve el foco a la vista y el catálogo queda
    // "con foco" sin ningún póster enfocado, así que esa primera vez no se confía en catalogHasFocus.
    val summaryReady = if (showSummary) viewModel.summaryContent.collectAsState().value.ready else false
    val shownStale = if (showSummary) !summaryReady else contentStale
    LaunchedEffect(railExpanded, shownStale, showSummary) {
        if (!railExpanded && !shownStale && profile.isTv) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            withFrameNanos { }
            if (focusRestored && catalogHasFocus) {
                railWasOpen = false
                return@LaunchedEffect
            }
            // Derecha o Atrás en el menú de TV vuelven un paso: a la columna de categorías si la hay.
            if (railWasOpen) {
                railWasOpen = false
                if (focusRestored && tvCategoryColumn) {
                    tvCatsFocusRequests++
                    return@LaunchedEffect
                }
            }
            // Recorriendo la columna de categorías el contenido cambia solo: el foco se queda en ella.
            if (focusRestored && tvCatsHaveFocus && !contentFocusPending) return@LaunchedEffect
            contentFocusPending = false
            if (!focusRestored && focusMemory.target != null) {
                focusRestored = true
                val restored = focusInCatalog(restoreFocus)
                withFrameNanos { }
                if (restored && catalogHasFocus) return@LaunchedEffect
            }
            focusRestored = true
            // Recién abierta la búsqueda el foco es del campo: En vivo se lo quitaba.
            if (searchOpen && ui.query.isBlank()) {
                runCatching { searchFocus.requestFocus() }
                return@LaunchedEffect
            }
            if (tvLive) {
                liveFocusRequests++
            } else {
                val last = tvNav?.lastRequester()
                if (!(last != null && focusInCatalog(last) && run { withFrameNanos { }; catalogHasFocus })) {
                    runCatching { catalogFocus.requestFocus() }
                }
            }
        }
    }

    fun openSearch() {
        searchOpen = true
        pillVisible.value = true
    }

    /** Derecha u OK en la columna de categorías: al último póster de la categoría o al primero. */
    fun focusTvContent() {
        if (contentStale) {
            contentFocusPending = true
            return
        }
        scope.launch {
            withFrameNanos { }
            val last = tvNav?.lastRequester()
            if (!(last != null && focusInCatalog(last))) runCatching { catalogFocus.requestFocus() }
        }
    }

    // TV: el "Ver todo" que tenía el foco desaparece al abrir la cuadrícula; al cerrarla se
    // vuelve a él. Solo en el cambio: al recrearse Inicio el foco lo devuelve FocusMemory.
    val continueAllFocus = remember { FocusRequester() }
    var continueAllShown by remember { mutableStateOf(showContinueAll) }
    LaunchedEffect(showContinueAll) {
        if (showContinueAll == continueAllShown) return@LaunchedEffect
        continueAllShown = showContinueAll
        if (!profile.isTv) return@LaunchedEffect
        repeat(3) {
            withFrameNanos { }
            val target = if (showContinueAll) continueAllFocus else tvNav?.lastRequester()
            if (target != null && focusInCatalog(target)) return@LaunchedEffect
        }
        runCatching { catalogFocus.requestFocus() }
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(150)
            runCatching { searchFocus.requestFocus() }
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            val text = it.resolve(context)
            scope.launch { snackbar.showSnackbar(text) }
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

    // Celular: desde una sección, Atrás vuelve al resumen; desde el resumen sale de la app.
    BackHandler(enabled = profile.compactRail && !searchOpen && ui.category == null && !ui.summary) {
        viewModel.showSummary()
    }

    BackHandler(enabled = showContinueAll) { continueAllIn = null }

    /** TV: muestra Inicio detrás del menú, que sigue abierto. */
    fun showTvHome() {
        if (searchOpen) searchOpen = false
        tvHomeChosen = true
        viewModel.showSummary()
    }

    fun goHome() {
        showTvHome()
        setRailExpanded(false)
    }

    val onItemClick: (BrowseItem) -> Unit = onOpen
    // Dejar presionado agrega o quita de Mi lista; el menú completo va con la tecla Menú.
    val onItemLongClick: (BrowseItem) -> Unit = viewModel::toggleFavorite
    // En "Seguir viendo" dejar presionado abre el menú: ahí sirve quitarlo o empezar de nuevo.
    val openContinueMenu: (ContinueItem) -> Unit = { item ->
        viewModel.browseItemFor(item) {
            menuContinue = item
            menuItem = it
        }
    }
    val openContinueAll: () -> Unit = { continueAllIn = continueContext }

    /** Las categorías de adultos bloqueadas piden el PIN antes de abrirse. */
    fun openCategory(name: String?) {
        if (name != null && name in content.lockedCategories) {
            pinError = null
            pinStep = PinStep.Unlock(name)
        } else viewModel.selectCategory(name)
    }

    /** En el perfil de niños lo de adultos (Configuración, organizar categorías) pide un PIN de adultos. */
    fun guarded(action: () -> Unit) {
        profileActions?.guardAdult?.invoke(action) ?: action()
    }

    fun openSettings() = guarded { showSettings = true }

    fun showOrganizeDialog() {
        viewModel.organize(ui.section)
        showOrganize = true
    }

    fun openOrganize() = guarded(::showOrganizeDialog)

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

    val lastRepeatAt = remember { longArrayOf(0L) }
    val repeatGapMs = if (profile.lowRam) KEY_REPEAT_GAP_LOW_RAM_MS else KEY_REPEAT_GAP_MS

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding -> CompositionLocalProvider(LocalFavoriteKeys provides favorites) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onPreviewKeyEvent { e ->
                    // Con la flecha apretada el control repite unas 20 veces por segundo, más de lo
                    // que un TV alcanza a dibujar: las pulsaciones se acumulan y el foco se pasa de largo.
                    if (profile.isTv && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 && e.key in DPAD_KEYS) {
                        val at = e.nativeKeyEvent.eventTime
                        if (at - lastRepeatAt[0] < repeatGapMs) return@onPreviewKeyEvent true
                        lastRepeatAt[0] = at
                    }
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
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                PosterThumbs.clear(context)
                                ImageFailures.get(context).clear()
                            }
                            snackbar.showSnackbar(context.getString(R.string.home_images_cleared))
                        }
                    },
                    onResetAll = { showResetAll = true },
                    onSettings = ::openSettings,
                    onDownloads = onOpenDownloads,
                    onGuide = openGuide,
                    onLogo = if (profile.compactRail) ::goHome else null,
                    headerFocus = headerFocus,
                    showClock = showClock,
                    lastUpdate = lastUpdateLabel(lastRefreshAt),
                    sortOptions = if (content.grid && !contentStale && content.rows.sumOf { it.items.size } > 1) {
                        content.sortOptions
                    } else emptyList(),
                    sort = ui.sort,
                    onSort = viewModel::selectSort
                )

                if (profile.isPhone && !showSummary && !searchOpen && !contentStale) {
                    CategoryChips(
                        categories = content.categories,
                        labels = content.categoryLabels,
                        locked = content.lockedCategories,
                        myListCount = content.myListCount,
                        selected = ui.category,
                        onSelect = ::openCategory,
                        contentPadding = PaddingValues(horizontal = profile.edge),
                        modifier = Modifier.padding(vertical = 4.dp)
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
                            .focusRequester(searchFocus)
                            // El campo de texto se queda con las flechas: en TV abajo baja a los resultados.
                            .onPreviewKeyEvent { event ->
                                if (!profile.isTv || event.key != Key.DirectionDown || ui.query.isBlank()) return@onPreviewKeyEvent false
                                if (event.type == KeyEventType.KeyDown) {
                                    keyboard?.hide()
                                    runCatching { catalogFocus.requestFocus() }
                                }
                                true
                            },
                        placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (ui.query.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_search_clear))
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

                // Sin "deslizar para actualizar": se actualiza solo con el botón (en el celular, en
                // el menú de tres puntos) para no recargar sin querer al subir al principio.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (openCategoriesOnSwipe) Modifier.nestedScroll(swipeToCategories).pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onHorizontalDrag = { change, amount ->
                                    total += amount
                                    if (total > swipeOpenPx) {
                                        change.consume()
                                        total = Float.NEGATIVE_INFINITY
                                        setRailExpanded(true)
                                    }
                                }
                            )
                        } else Modifier)
                ) {
                    Row(Modifier.fillMaxSize()) {
                    if (tvCategoryColumn) {
                        // Mientras se calcula otra sección no se muestran las categorías de la anterior.
                        val fresh = content.section == ui.section && content.playlistId == ui.selectedPlaylistId
                        TvCategoryColumn(
                            categories = if (fresh) content.categories else emptyList(),
                            labels = content.categoryLabels,
                            counts = if (fresh) content.categoryCounts else emptyMap(),
                            locked = content.lockedCategories,
                            myListCount = if (fresh) content.myListCount else 0,
                            selected = ui.category,
                            // Con el menú abierto la sección puede cambiar detrás: la que aparece no se lleva el foco.
                            focusRequests = if (railExpanded) 0 else tvCatsFocusRequests,
                            onSelect = viewModel::selectCategory,
                            onRest = viewModel::prefetchNeighbors,
                            onOpenLocked = ::openCategory,
                            onRight = ::focusTvContent,
                            onLeft = { setRailExpanded(true) },
                            onFocusChange = { tvCatsHaveFocus = it },
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 12.dp),
                            sortOptions = if (fresh) content.sortOptions else emptyList(),
                            sort = ui.sort,
                            onSort = viewModel::selectSort
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
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
                        // Tampoco restoreFocusedChild(): pide el foco dentro del movimiento y deja las
                        // flechas sin responder. Se cancela la entrada y se pide en el frame siguiente.
                        .focusProperties {
                            onEnter = {
                                val target = tvNav?.lastRequester()
                                if (!catalogFocusFromCode.get() && target != null) {
                                    scope.launch {
                                        withFrameNanos { }
                                        focusInCatalog(target)
                                    }
                                    cancelFocusChange()
                                }
                            }
                            // Con la columna de categorías, izquierda vuelve a la elegida y no a
                            // la que quede enfrente.
                            onExit = {
                                if (requestedFocusDirection == FocusDirection.Left && tvCategoryColumn) {
                                    tvCatsFocusRequests++
                                    cancelFocusChange()
                                }
                            }
                        }
                        .focusGroup()
                        .onPreviewKeyEvent { event ->
                            when {
                                // Tecla Menú del control: abre el menú del póster enfocado.
                                event.key == Key.Menu -> {
                                    if (event.type == KeyEventType.KeyUp) {
                                        focusedContinue?.let(openContinueMenu) ?: focusedItem?.let { menuItem = it }
                                    }
                                    true
                                }
                                tvNav != null && tvNav.handles(event.key) -> {
                                    if (event.type == KeyEventType.KeyDown) {
                                        val dir = when (event.key) {
                                            Key.DirectionUp -> -1
                                            Key.DirectionDown -> 1
                                            else -> 0
                                        }
                                        if (dir != 0) scope.launch {
                                            tvNav.move(dir) {
                                                runCatching { (if (searchOpen) searchFocus else headerFocus).requestFocus() }
                                            }
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                    val bottomSpace = if (profile.compactRail) PILL_SPACE else 32.dp
                    val nothingYet = content.rows.isEmpty() && content.categories.isEmpty()
                    when {
                        showContinueAll && ui.playlists.isNotEmpty() -> ContinueGrid(
                            profile = profile,
                            items = if (showSummary) viewModel.summaryContinue.collectAsState().value else continueWatching,
                            bottomSpace = bottomSpace,
                            focusMemory = focusMemory,
                            tvNav = tvNav,
                            firstFocus = continueAllFocus,
                            onBack = { continueAllIn = null },
                            onPlay = ::playContinue,
                            onRemove = viewModel::removeFromContinue,
                            onMenu = openContinueMenu,
                            onFocusItem = onFocusContinue,
                            modifier = catalogModifier
                        )
                        showSummary && ui.playlists.isNotEmpty() -> {
                            val summary by viewModel.summaryContent.collectAsState()
                            val summaryContinue by viewModel.summaryContinue.collectAsState()
                            PhoneSummary(
                                profile = profile,
                                summary = summary,
                                continueWatching = summaryContinue,
                                firstSync = ui.firstSync,
                                bottomSpace = bottomSpace,
                                focusMemory = focusMemory,
                                tvNav = tvNav,
                                onFocusItem = onFocusItem,
                                onClick = onItemClick,
                                onLongClick = onItemLongClick,
                                onPlayContinue = ::playContinue,
                                onRemoveContinue = viewModel::removeFromContinue,
                                onContinueMenu = openContinueMenu,
                                onFocusContinue = onFocusContinue,
                                onContinueSeeAll = openContinueAll,
                                onSeeAll = { row ->
                                    when (row.title) {
                                        SUMMARY_MOVIES -> viewModel.openSection(Section.MOVIES, RECENT_ROW)
                                        SUMMARY_SERIES -> viewModel.openSection(Section.SERIES, RECENT_ROW)
                                        SUMMARY_EVENTS -> viewModel.openSection(Section.LIVE, summary.eventsCategory)
                                    }
                                },
                                modifier = catalogModifier
                            )
                        }
                        // Mientras se calcula la nueva sección no se muestran las filas de la anterior:
                        // el foco caería en un póster que desaparece y las flechas dejarían de responder.
                        contentStale || ui.playlists.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        tvLive -> TvLiveScreen(
                            playlistId = ui.selectedPlaylistId,
                            categories = content.categories,
                            categoryLabels = content.categoryLabels,
                            lockedCategories = content.lockedCategories,
                            onOpenMenu = { setRailExpanded(true) },
                            onUnlock = { pinError = null; pinStep = PinStep.Unlock(null) },
                            onFullscreen = { onPlayLiveFullscreen(it.id) },
                            canTakeFocus = !railExpanded,
                            focusRequests = if (railExpanded) 0 else liveFocusRequests,
                            onExitUp = { runCatching { (if (searchOpen) searchFocus else headerFocus).requestFocus() } },
                            onOpenGuide = { category -> ui.selectedPlaylistId?.let { onOpenGuide(it, category) } },
                            onCategoryShown = { liveCategory = it },
                            sortOptions = if (content.section == ui.section && content.playlistId == ui.selectedPlaylistId) {
                                content.sortOptions
                            } else emptyList(),
                            sort = ui.sort,
                            onSort = viewModel::selectSort,
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged {
                                    catalogHasFocus = it.hasFocus
                                    if (it.hasFocus && railExpanded) railExpanded = false
                                }
                                .focusRequester(catalogFocus)
                        )
                        ui.firstSync && nothingYet && !searching -> PreparingCatalog()
                        content.grid -> CatalogGrid(
                            profile = profile,
                            content = content,
                            title = when (ui.category) {
                                SEARCH_MOVIES -> stringResource(R.string.home_search_movies_for, ui.query.trim())
                                SEARCH_SERIES -> stringResource(R.string.home_search_series_for, ui.query.trim())
                                SEARCH_LIVE -> stringResource(R.string.home_search_channels_for, ui.query.trim())
                                MY_LIST_ROW -> stringResource(R.string.home_my_list_in_section, ui.section.label)
                                ALL_ROW -> stringResource(R.string.home_all_in_section, ui.section.label)
                                else -> ui.category?.let { content.categoryLabels[it] ?: rowLabel(it).asString() }.orEmpty()
                            },
                            emptyText = when {
                                searching -> stringResource(R.string.home_search_empty, ui.query.trim())
                                ui.category == MY_LIST_ROW -> stringResource(R.string.home_my_list_empty)
                                else -> stringResource(R.string.home_category_empty)
                            },
                            showBack = ui.category != null && !tvCategoryColumn,
                            onBack = { viewModel.selectCategory(null) },
                            // El orden va en el botón de la cabecera; en TV tampoco hay título.
                            showTitle = !profile.isTv || !tvCategoryColumn,
                            stateKey = "${ui.section}|${ui.category}|${ui.query.trim()}",
                            sort = ui.sort,
                            bottomSpace = bottomSpace,
                            focusMemory = focusMemory,
                            tvNav = tvNav,
                            onClick = onItemClick,
                            onLongClick = onItemLongClick,
                            onFocusItem = onFocusItem,
                            modifier = catalogModifier,
                            listView = if (profile.isTv) null else gridSection.name in listViewSections,
                            onListViewChange = { viewModel.setListView(gridSection, it) },
                            upToHeader = if (tvCategoryColumn) (if (searchOpen) searchFocus else headerFocus) else null
                        )
                        else -> CatalogRows(
                            profile = profile,
                            section = ui.section,
                            content = content,
                            continueWatching = if (searching) emptyList() else continueWatching,
                            emptyText = if (searching) stringResource(R.string.home_search_empty, ui.query.trim())
                            else stringResource(R.string.home_section_empty, ui.section.label),
                            stateKey = if (searching) "search" else ui.section.name,
                            bottomSpace = bottomSpace,
                            focusMemory = focusMemory,
                            tvNav = tvNav,
                            onClick = onItemClick,
                            onLongClick = onItemLongClick,
                            onFocusItem = onFocusItem,
                            onPlayContinue = ::playContinue,
                            onRemoveContinue = viewModel::removeFromContinue,
                            onContinueMenu = openContinueMenu,
                            onFocusContinue = onFocusContinue,
                            onContinueSeeAll = openContinueAll,
                            onSeeAll = { row -> openCategory(row.title) },
                            modifier = catalogModifier
                        )
                    }
                    }
                    }
                }
            }

            if (ui.playlists.isNotEmpty()) {
                NavRail(
                    profile = profile,
                    section = ui.section,
                    searchActive = searchOpen,
                    // Mientras se calcula la sección nueva, las de la anterior no se ofrecen.
                    categories = if (searching || contentStale) emptyList() else content.categories,
                    categoryCounts = if (contentStale) emptyMap() else content.categoryCounts,
                    categoryLabels = content.categoryLabels,
                    lockedCategories = content.lockedCategories,
                    selectedCategory = ui.category,
                    showCategories = !searching && !showSummary,
                    myListCount = if (contentStale) 0 else content.myListCount,
                    expanded = railExpanded,
                    pillVisible = pillVisible.value,
                    onExpandedChange = ::setRailExpanded,
                    onSearch = ::openSearch,
                    summaryActive = showSummary,
                    onHome = ::goHome,
                    onHomePreview = ::showTvHome,
                    onSection = {
                        if (searchOpen) {
                            viewModel.onQueryChange("")
                            searchOpen = false
                        }
                        if (profile.compactRail) viewModel.openSection(it) else viewModel.selectSection(it)
                        if (!profile.isTv) setRailExpanded(false)
                    },
                    onCategory = ::openCategory,
                    onCategoryMenu = {
                        guarded {
                            viewModel.organize(ui.section)
                            categoryMenu = it
                        }
                    },
                    onOrganize = ::openOrganize,
                    onSettings = ::openSettings,
                    profileLabel = profileMenu?.let { stringResource(R.string.menu_profile, profileDisplayName(it.name)) },
                    profileTint = profileMenu?.let { profileColor(it.avatar) },
                    onProfile = { profileActions?.onSwitch?.invoke() },
                    onDownloads = {
                        setRailExpanded(false)
                        onOpenDownloads()
                    },
                    isUserMoveToStrip = { SystemClock.uptimeMillis() - lastLeftKeyAt < STRIP_KEY_WINDOW_MS },
                    onStrayStripFocus = {
                        if (tvLive) liveFocusRequests++
                        else scope.launch {
                            withFrameNanos { }
                            runCatching { catalogFocus.requestFocus() }
                        }
                    }
                )
            }
        }
    } }

    menuItem?.let { item ->
        val inList = item.key in favorites.value
        val inProgress = continueWatching.any { it.key == item.key }
        val fromContinue = menuContinue?.takeIf { it.key == item.key }
        val myListAction = ContentAction(
            if (inList) Icons.Default.Check else Icons.Default.Add,
            stringResource(if (inList) R.string.home_action_remove_my_list else R.string.home_action_add_my_list)
        ) { viewModel.toggleFavorite(item) }
        val actions = if (fromContinue != null) buildList {
            add(ContentAction(Icons.Default.PlayArrow, stringResource(R.string.home_action_play)) { playContinue(fromContinue) })
            add(ContentAction(Icons.Default.Info, stringResource(R.string.home_action_details)) { onOpen(item) })
            add(myListAction)
            add(ContentAction(Icons.Default.Close, stringResource(R.string.home_action_remove_continue)) { viewModel.removeFromContinue(fromContinue) })
            add(ContentAction(Icons.Default.Replay, stringResource(R.string.home_action_restart_progress), destructive = true) { viewModel.resetProgress(item) })
        } else buildList {
            add(ContentAction(Icons.Default.PlayArrow, stringResource(if (inProgress) R.string.home_continue_watching else R.string.home_action_play)) {
                when (item) {
                    is BrowseItem.Movie -> onPlayMovie(item.movie.id)
                    is BrowseItem.Show -> viewModel.playSeries(item.series, onPlayEpisode)
                    is BrowseItem.Live -> onOpen(item)
                }
            })
            if (item !is BrowseItem.Live) {
                add(ContentAction(Icons.Default.Info, stringResource(R.string.home_action_details)) { onOpen(item) })
            }
            add(myListAction)
            if (item is BrowseItem.Movie) {
                add(ContentAction(Icons.Default.DoneAll, stringResource(R.string.home_action_mark_watched)) { viewModel.markWatched(item.movie) })
            }
            if (inProgress) {
                add(ContentAction(Icons.Default.Close, stringResource(R.string.home_action_remove_continue)) { viewModel.removeFromContinue(item) })
            }
            if (item !is BrowseItem.Live) {
                add(
                    ContentAction(
                        Icons.Default.Replay,
                        stringResource(if (item is BrowseItem.Show) R.string.home_action_restart_series else R.string.home_action_restart_progress),
                        destructive = true
                    ) { viewModel.resetProgress(item) }
                )
            }
            add(
                ContentAction(
                    Icons.Default.VisibilityOff,
                    stringResource(if (item is BrowseItem.Live) R.string.home_action_hide_channel else R.string.home_action_hide_title)
                ) { viewModel.hideItem(item) }
            )
        }
        fun group(name: String) = TitleCleaner.clean(name, catalogPrefs.cleanNames).ifBlank { null }
        ContentMenu(
            title = item.title,
            subtitle = when (item) {
                is BrowseItem.Movie -> listOfNotNull(stringResource(R.string.home_kind_movie), group(item.movie.categoryName), ratingText(item.rating)).joinToString(" · ")
                is BrowseItem.Show -> listOfNotNull(stringResource(R.string.home_kind_series), group(item.series.categoryName), ratingText(item.rating)).joinToString(" · ")
                is BrowseItem.Live -> listOfNotNull(stringResource(R.string.home_kind_live_channel), group(item.channel.groupTitle)).joinToString(" · ")
            },
            imageUrl = item.imageUrl,
            actions = actions,
            onDismiss = {
                menuItem = null
                menuContinue = null
            }
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
                error = accountError?.asString(),
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
            title = { Text(stringResource(R.string.home_logout)) },
            text = { Text(stringResource(R.string.home_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    showSettings = false
                    viewModel.logout()
                }) { Text(stringResource(R.string.home_logout), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.home_cancel)) } }
        )
    }

    confirmAdult?.let { change ->
        AlertDialog(
            onDismissRequest = { confirmAdult = null },
            title = { Text(stringResource(R.string.home_mark_adult)) },
            text = {
                Text(
                    stringResource(
                        if (pinSet) R.string.home_mark_adult_message_pin else R.string.home_mark_adult_message_no_pin,
                        change.label
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAdult = null
                    applyChange(change)
                }) { Text(stringResource(R.string.home_mark_adult_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmAdult = null }) { Text(stringResource(R.string.home_cancel)) } }
        )
    }

    if (showResetAll) {
        AlertDialog(
            onDismissRequest = { showResetAll = false },
            title = { Text(stringResource(R.string.home_reset_all_title)) },
            text = { Text(stringResource(R.string.home_reset_all_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetAllProgress(); showResetAll = false }) {
                    Text(stringResource(R.string.home_reset_all_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetAll = false }) { Text(stringResource(R.string.home_cancel)) } }
        )
    }

    categoryMenu?.let { name ->
        val info = organizeList.firstOrNull { it.name == name }
        val adult = info?.adult ?: false
        val label = content.categoryLabels[name] ?: rowLabel(name).asString()
        val actions = buildList {
            add(ContentAction(Icons.Default.PlayArrow, stringResource(R.string.home_category_open)) { openCategory(name) })
            add(ContentAction(Icons.Default.KeyboardArrowUp, stringResource(R.string.home_move_up)) { viewModel.moveCategory(ui.section, name, -1) })
            add(ContentAction(Icons.Default.KeyboardArrowDown, stringResource(R.string.home_move_down)) { viewModel.moveCategory(ui.section, name, 1) })
            add(ContentAction(Icons.Default.VisibilityOff, stringResource(R.string.home_category_hide)) { viewModel.setCategoryHidden(ui.section, name, true) })
            add(
                ContentAction(
                    if (adult) Icons.Default.LockOpen else Icons.Default.Lock,
                    stringResource(if (adult) R.string.home_unmark_adult else R.string.home_mark_adult)
                ) {
                    requestAdultChange(
                        GuardedChange(
                            ui.section, name, label,
                            if (adult) GuardedChange.Kind.UNMARK_ADULT else GuardedChange.Kind.MARK_ADULT
                        )
                    )
                }
            )
            add(ContentAction(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.home_organize_categories)) { showOrganizeDialog() })
        }
        ContentMenu(
            title = label,
            subtitle = listOfNotNull(
                content.categoryCounts[name]?.let { pluralStringResource(R.plurals.home_titles_count, it, it) },
                if (adult) stringResource(R.string.home_adult_tag) else null
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
            accountInfo = accountInfo,
            onEditAccount = {
                viewModel.clearAccountError()
                showAccount = true
            },
            onLogout = { confirmLogout = true },
            profileName = profileMenu?.let { profileDisplayName(it.name) },
            onSwitchProfile = {
                showSettings = false
                profileActions?.onSwitch?.invoke()
            },
            onManageProfiles = {
                showSettings = false
                profileActions?.onManage?.invoke()
            },
            appVersion = updateViewModel.versionName,
            onCheckUpdates = updateViewModel::checkNow,
            appLanguage = remember { AppLanguage.current(context) },
            onAppLanguageChange = { AppLanguage.set(context, it) },
            showClock = showClock,
            onShowClockChange = viewModel::setShowClock,
            playerPrefs = playerPrefs,
            onContentLanguageChange = viewModel::setContentLanguage,
            onSubtitlesByDefaultChange = viewModel::setSubtitlesByDefault,
            onSubtitleSizeChange = viewModel::setSubtitleSize,
            onSubtitleColorChange = viewModel::setSubtitleColor,
            onSubtitleBackgroundChange = viewModel::setSubtitleBackground,
            onSubtitlePositionChange = viewModel::setSubtitlePosition,
            onLiveFormatChange = viewModel::setLiveFormat,
            onDecoderChange = viewModel::setDecoder,
            isTv = profile.isTv,
            onFrameRateModeChange = viewModel::setFrameRateMode,
            onTunnelingChange = viewModel::setTunneling,
            onSkipIntroChange = viewModel::setSkipIntroButton,
            refreshMode = refreshMode,
            onRefreshModeChange = viewModel::setRefreshMode,
            lastRefreshLabel = lastUpdateLabel(lastRefreshAt),
            onRefreshNow = {
                showSettings = false
                viewModel.refreshAll()
            },
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
            onOrganize = { showOrganizeDialog() },
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
        val pinWrong = stringResource(R.string.home_pin_wrong)
        val pinMismatch = stringResource(R.string.home_pin_mismatch)
        PinDialog(
            title = when (step) {
                is PinStep.Unlock, is PinStep.Guard -> stringResource(R.string.home_pin_enter)
                PinStep.Create, is PinStep.GuardCreate -> stringResource(R.string.home_pin_new)
                is PinStep.Confirm, is PinStep.GuardConfirm -> stringResource(R.string.home_pin_repeat)
                PinStep.VerifyForChange, PinStep.VerifyForRemove -> stringResource(R.string.home_pin_current)
            },
            message = when (step) {
                is PinStep.Unlock -> stringResource(R.string.home_pin_unlock_message)
                is PinStep.Guard -> when (step.change.kind) {
                    GuardedChange.Kind.UNHIDE -> stringResource(R.string.home_pin_guard_unhide, step.change.label)
                    else -> stringResource(R.string.home_pin_guard_unmark, step.change.label)
                }
                is PinStep.GuardCreate -> stringResource(R.string.home_pin_guard_create)
                PinStep.Create -> stringResource(R.string.home_pin_create_message)
                is PinStep.Confirm, is PinStep.GuardConfirm -> stringResource(R.string.home_pin_confirm_message)
                PinStep.VerifyForChange -> stringResource(R.string.home_pin_verify_change)
                PinStep.VerifyForRemove -> stringResource(R.string.home_pin_verify_remove)
            },
            error = pinError,
            onSubmit = { pin ->
                when (step) {
                    is PinStep.Unlock -> viewModel.unlockParental(pin) { ok ->
                        if (ok) {
                            pinStep = null
                            pinError = null
                            step.category?.let(viewModel::selectCategory)
                        } else pinError = pinWrong
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
                        pinError = pinMismatch
                        pinStep = PinStep.Create
                    }
                    is PinStep.Guard -> viewModel.verifyPin(pin) { ok ->
                        if (ok) {
                            pinStep = null
                            pinError = null
                            applyChange(step.change)
                        } else pinError = pinWrong
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
                        pinError = pinMismatch
                        pinStep = PinStep.GuardCreate(step.change)
                    }
                    PinStep.VerifyForChange -> viewModel.unlockParental(pin) { ok ->
                        if (ok) {
                            pinError = null
                            pinStep = PinStep.Create
                        } else pinError = pinWrong
                    }
                    PinStep.VerifyForRemove -> viewModel.clearPin(pin) { ok ->
                        if (ok) {
                            pinStep = null
                            pinError = null
                        } else pinError = pinWrong
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
        Text(stringResource(R.string.home_preparing_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.home_preparing_message),
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
    tvNav: TvCatalogNav?,
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onFocusItem: (BrowseItem) -> Unit,
    onPlayContinue: (ContinueItem) -> Unit,
    onRemoveContinue: (ContinueItem) -> Unit,
    onContinueMenu: (ContinueItem) -> Unit,
    onFocusContinue: (ContinueItem) -> Unit,
    onContinueSeeAll: () -> Unit,
    onSeeAll: (ContentRow) -> Unit,
    modifier: Modifier
) {
    // Guardado: Inicio se recrea al volver de la ficha y debe quedar donde estaba.
    val listState = rememberSaveable(stateKey, saver = LazyListState.Saver) { LazyListState() }
    RevealContinueRow(listState, continueWatching.isNotEmpty())
    val myList = content.rows.firstOrNull { it.title == MY_LIST_ROW && !content.isSearch }
    val others = content.rows.filter { it !== myList && it.items.isNotEmpty() }
    // Mismo orden que los ítems del LazyColumn: las flechas del control lo usan para ir de fila en fila.
    val rowOrder = buildList {
        if (myList != null) add("row:${myList.title}")
        if (continueWatching.isNotEmpty()) add("continue")
        if (content.top10.isNotEmpty()) add("top10")
        others.forEach { add("row:${it.title}") }
    }.flatMap { if (it.startsWith("row:") || it == "continue") listOf(it, it + TvCatalogNav.ALL_SUFFIX) else listOf(it) }
    if (tvNav != null) {
        tvNav.rowOrder = rowOrder
        tvNav.columnState = listState
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(profile.rowGap)
    ) {
        if (myList != null) {
            item(key = "row:${myList.title}", contentType = "row") {
                CatalogRow(profile, myList, focusMemory, onClick, onLongClick, onFocusItem, onSeeAll = { onSeeAll(myList) },
                    tvNav = tvNav, navKey = "row:${myList.title}")
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue", contentType = "continue") {
                ContinueWatchingRow(
                    profile = profile,
                    items = continueWatching,
                    focusMemory = focusMemory,
                    onPlay = onPlayContinue,
                    onRemove = onRemoveContinue,
                    onMenu = onContinueMenu,
                    onFocusItem = onFocusContinue,
                    onSeeAll = onContinueSeeAll,
                    tvNav = tvNav,
                    navKey = "continue"
                )
            }
        }
        if (content.top10.isNotEmpty()) {
            item(key = "top10", contentType = "top10") {
                Top10Row(
                    profile = profile,
                    title = content.top10Label.asString(),
                    items = content.top10,
                    focusMemory = focusMemory,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onFocusItem = onFocusItem,
                    tvNav = tvNav,
                    navKey = "top10"
                )
            }
        }
        others.forEach { row ->
            item(key = "row:${row.title}", contentType = "row") {
                CatalogRow(profile, row, focusMemory, onClick, onLongClick, onFocusItem, onSeeAll = { onSeeAll(row) },
                    tvNav = tvNav, navKey = "row:${row.title}")
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

/**
 * La lista se queda anclada a la fila que se veía arriba: si "Seguir viendo" aparece encima
 * (p. ej. al cambiar de perfil), quedaría fuera de la pantalla. Estando arriba, se vuelve a mostrar.
 */
@Composable
private fun RevealContinueRow(listState: LazyListState, hasContinue: Boolean) {
    var hadContinue by remember { mutableStateOf(hasContinue) }
    LaunchedEffect(hasContinue) {
        if (hasContinue && !hadContinue && listState.firstVisibleItemIndex <= 2) listState.scrollToItem(0)
        hadContinue = hasContinue
    }
}

// ============================ Resumen (celular) ============================

@Composable
private fun PhoneSummary(
    profile: DeviceProfile,
    summary: SummaryContent,
    continueWatching: List<ContinueItem>,
    firstSync: Boolean,
    bottomSpace: Dp,
    focusMemory: FocusMemory,
    tvNav: TvCatalogNav?,
    onFocusItem: (BrowseItem) -> Unit,
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onPlayContinue: (ContinueItem) -> Unit,
    onRemoveContinue: (ContinueItem) -> Unit,
    onContinueMenu: (ContinueItem) -> Unit,
    onFocusContinue: (ContinueItem) -> Unit,
    onContinueSeeAll: () -> Unit,
    onSeeAll: (ContentRow) -> Unit,
    modifier: Modifier
) {
    val nothing = summary.rows.isEmpty() && continueWatching.isEmpty()
    when {
        !summary.ready -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        nothing && firstSync -> PreparingCatalog()
        else -> {
            // Guardado: Inicio se recrea al volver de la ficha y debe quedar donde estaba.
            val listState = rememberSaveable("summary", saver = LazyListState.Saver) { LazyListState() }
            RevealContinueRow(listState, continueWatching.isNotEmpty())
            if (tvNav != null) {
                tvNav.rowOrder = buildList {
                    if (continueWatching.isNotEmpty()) { add("continue"); add("continue" + TvCatalogNav.ALL_SUFFIX) }
                    summary.rows.forEach { add("row:${it.title}"); add("row:${it.title}" + TvCatalogNav.ALL_SUFFIX) }
                }
                tvNav.columnState = listState
            }
            LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomSpace),
                verticalArrangement = Arrangement.spacedBy(profile.rowGap)
            ) {
                if (continueWatching.isNotEmpty()) {
                    item(key = "continue", contentType = "continue") {
                        ContinueWatchingRow(
                            profile = profile,
                            items = continueWatching,
                            focusMemory = focusMemory,
                            onPlay = onPlayContinue,
                            onRemove = onRemoveContinue,
                            onMenu = onContinueMenu,
                            onFocusItem = onFocusContinue,
                            onSeeAll = onContinueSeeAll,
                            tvNav = tvNav,
                            navKey = "continue"
                        )
                    }
                }
                summary.rows.forEach { row ->
                    item(key = "row:${row.title}", contentType = "row") {
                        CatalogRow(profile, row, focusMemory, onClick, onLongClick, onFocusItem, onSeeAll = { onSeeAll(row) },
                            tvNav = tvNav, navKey = "row:${row.title}")
                    }
                }
                if (nothing) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.home_summary_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = profile.edge, vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================ Cuadrícula "Ver todo" ============================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CatalogGrid(
    profile: DeviceProfile,
    content: SectionContent,
    title: String,
    emptyText: String,
    showBack: Boolean,
    onBack: () -> Unit,
    showTitle: Boolean,
    stateKey: String,
    sort: SortOrder?,
    bottomSpace: Dp,
    focusMemory: FocusMemory,
    tvNav: TvCatalogNav?,
    onClick: (BrowseItem) -> Unit,
    onLongClick: (BrowseItem) -> Unit,
    onFocusItem: (BrowseItem) -> Unit,
    modifier: Modifier,
    /** Vista en lista (un renglón por título); null = solo cuadrícula, sin botón para cambiarla. */
    listView: Boolean? = null,
    onListViewChange: (Boolean) -> Unit = {},
    /** TV: arriba desde la primera fila va aquí (la cabecera); si no, la búsqueda de foco acaba en la tira del menú. */
    upToHeader: FocusRequester? = null
) {
    val gridState = rememberPosterGridState(profile, stateKey, sort)
    val total = content.rows.sumOf { it.items.size }
    val multiSection = content.rows.count { it.items.isNotEmpty() } > 1
    val list = listView == true
    // Cada encabezado ocupa un renglón entero: la primera fila de pósters va después de ellos.
    val firstItemLine = listOf(showTitle, multiSection).count { it }
    LazyVerticalGrid(
        columns = if (list) GridCells.Fixed(1) else GridCells.Adaptive(minSize = profile.posterWidth),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(start = profile.edge, end = profile.edge, top = 4.dp, bottom = bottomSpace),
        horizontalArrangement = Arrangement.spacedBy(profile.itemGap),
        verticalArrangement = Arrangement.spacedBy(if (list) 2.dp else profile.itemGap)
    ) {
        if (showTitle) item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                if (showBack) {
                    // Arriba de "Volver" solo queda la tira del menú: el foco rebotaba y la cuadrícula
                    // parecía trabada. Al menú se llega con la flecha izquierda.
                    IconButton(onClick = onBack, modifier = Modifier.focusProperties { up = FocusRequester.Cancel }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.home_back_to_all))
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
                        pluralStringResource(R.plurals.home_titles_count, total, total),
                        style = MaterialTheme.typography.labelMedium,
                        color = EntreBrand.TextDim
                    )
                }
                if (listView != null) {
                    IconButton(onClick = { onListViewChange(!listView) }) {
                        Icon(
                            if (listView) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = stringResource(if (listView) R.string.home_view_grid else R.string.home_view_list)
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
                        "${row.label.asString()} · ${row.items.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            items(
                count = row.items.size,
                key = { index -> "${row.title}:${row.items[index].key}" },
                contentType = { index ->
                    when {
                        list -> "listItem"
                        row.items[index] is BrowseItem.Live -> "live"
                        else -> "poster"
                    }
                }
            ) { index ->
                val item = row.items[index]
                val memoryKey = "grid|${row.title}|${item.key}"
                val gridKey = "${row.title}:${item.key}"
                val itemModifier = focusMemory.modifierFor(memoryKey).tvNavItem(tvNav, "grid:${row.title}", index)
                    .then(
                        if (upToHeader == null) Modifier
                        else Modifier.focusProperties {
                            val line = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == gridKey }?.row
                            up = if (line == firstItemLine) upToHeader else FocusRequester.Default
                        }
                    )
                val onFocused = {
                    focusMemory.onFocused(memoryKey)
                    onFocusItem(item)
                }
                if (list) ListItemCard(
                    item = item,
                    modifier = itemModifier,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onFocused = onFocused
                ) else PosterCard(
                    profile = profile,
                    item = item,
                    fillWidth = true,
                    modifier = itemModifier,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onFocused = onFocused
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
    onFocusItem: (BrowseItem) -> Unit,
    tvNav: TvCatalogNav? = null,
    navKey: String = ""
) {
    val rowState = rememberTvRowState(tvNav, navKey, profile.rowPrefetch)
    Column {
        RowHeader(title, profile, onSeeAll = null)
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = profile.edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(profile.itemGap / 2)
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                val memoryKey = "top10|${item.key}"
                Top10Card(
                    profile = profile,
                    rank = index + 1,
                    item = item,
                    modifier = focusMemory.modifierFor(memoryKey).tvNavItem(tvNav, navKey, index),
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
    onDownloads: () -> Unit,
    /** TV En vivo: botón Guía; null lo oculta. */
    onGuide: (() -> Unit)? = null,
    /** Celular: tocar el logo vuelve al resumen. En TV es null para que no tome el foco. */
    onLogo: (() -> Unit)? = null,
    /** Lupa: a ella sube el foco desde la primera fila en TV. */
    headerFocus: FocusRequester = remember { FocusRequester() },
    showClock: Boolean = false,
    lastUpdate: String = "",
    /** Órdenes de la cuadrícula visible; vacío = sin botón de ordenar. */
    sortOptions: List<SortOrder> = emptyList(),
    sort: SortOrder? = null,
    onSort: (SortOrder) -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = profile.edge, end = profile.edge / 2, top = 6.dp, bottom = 2.dp)
    ) {
        Box(
            if (onLogo != null) {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = stringResource(R.string.home_home), onClick = onLogo)
            } else Modifier
        ) {
            EntreWordmark(fontSize = if (profile.isTv) 30.sp else 24.sp)
        }
        Spacer(Modifier.weight(1f))
        if (showClock) SmallClock(Modifier.padding(end = 8.dp), showDate = !profile.compactRail)
        // En el celular no entra junto a los botones: va dentro del menú de tres puntos.
        if (!profile.compactRail && lastUpdate.isNotEmpty()) {
            Text(
                lastUpdate,
                style = MaterialTheme.typography.labelMedium,
                color = EntreBrand.TextDim,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        if (onGuide != null) {
            TextButton(onClick = onGuide) {
                Icon(Icons.Default.CalendarViewDay, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.guide_button))
            }
        }
        IconButton(onClick = onToggleSearch, modifier = Modifier.focusRequester(headerFocus)) {
            Icon(
                if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                contentDescription = stringResource(if (searchOpen) R.string.home_search_close else R.string.home_search)
            )
        }
        if (sortOptions.size > 1) {
            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.home_sort))
                }
                SortMenu(
                    expanded = sortOpen,
                    options = sortOptions,
                    sort = sort,
                    focusCurrent = profile.isTv,
                    onSort = onSort,
                    onDismiss = { sortOpen = false }
                )
            }
        }
        // En el celular actualizar va solo en el menú de tres puntos; aquí queda el indicador mientras actualiza.
        if (!profile.compactRail) {
            IconButton(onClick = onRefresh) {
                if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.home_refresh))
            }
        } else if (refreshing) {
            CircularProgressIndicator(Modifier.padding(horizontal = 13.dp).size(22.dp), strokeWidth = 2.dp)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.home_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (profile.compactRail) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_refresh)) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        trailingIcon = if (lastUpdate.isNotEmpty()) {
                            { Text(lastUpdate, style = MaterialTheme.typography.labelSmall, color = EntreBrand.TextDim) }
                        } else null,
                        enabled = !refreshing,
                        onClick = { menuOpen = false; onRefresh() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_downloads)) },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                    onClick = { menuOpen = false; onDownloads() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_settings)) },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { menuOpen = false; onSettings() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_clear_image_cache)) },
                    onClick = { menuOpen = false; onClearImages() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_reset_all_menu)) },
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
    onRemove: (ContinueItem) -> Unit,
    onMenu: (ContinueItem) -> Unit,
    onFocusItem: (ContinueItem) -> Unit,
    onSeeAll: () -> Unit,
    tvNav: TvCatalogNav? = null,
    navKey: String = ""
) {
    val rowState = rememberTvRowState(tvNav, navKey, profile.rowPrefetch)
    Column {
        // Como en las demás filas: en TV "Ver todo" va al final y debajo de la fila.
        RowHeader(stringResource(R.string.home_continue_watching), profile, onSeeAll = if (profile.isTv) null else onSeeAll)
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = profile.edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(profile.itemGap)
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                val memoryKey = "continue|${item.key}"
                ContinueCard(
                    profile = profile,
                    item = item,
                    modifier = focusMemory.modifierFor(memoryKey).tvNavItem(tvNav, navKey, index),
                    onFocused = {
                        focusMemory.onFocused(memoryKey)
                        onFocusItem(item)
                    },
                    onPlay = { onPlay(item) },
                    onRemove = { onRemove(item) },
                    onMenu = { onMenu(item) }
                )
            }
            if (profile.isTv) {
                item(key = "seeAll", contentType = "seeAll") {
                    SeeAllCard(
                        profile = profile,
                        live = false,
                        onClick = onSeeAll,
                        modifier = Modifier.tvNavItem(tvNav, navKey, items.size)
                    )
                }
            }
        }
        if (profile.isTv && tvNav != null) {
            SeeAllButton(
                onClick = onSeeAll,
                modifier = Modifier
                    .padding(start = profile.edge, top = 2.dp)
                    .tvNavItem(tvNav, navKey + TvCatalogNav.ALL_SUFFIX, 0)
            )
        }
    }
}

/** "Ver todo" de Seguir viendo: todas las tarjetas en cuadrícula (la fila puede quedarse corta). */
@Composable
private fun ContinueGrid(
    profile: DeviceProfile,
    items: List<ContinueItem>,
    bottomSpace: Dp,
    focusMemory: FocusMemory,
    tvNav: TvCatalogNav?,
    firstFocus: FocusRequester,
    onBack: () -> Unit,
    onPlay: (ContinueItem) -> Unit,
    onRemove: (ContinueItem) -> Unit,
    onMenu: (ContinueItem) -> Unit,
    onFocusItem: (ContinueItem) -> Unit,
    modifier: Modifier
) {
    // Las filas no están en pantalla: arriba y abajo los resuelve la búsqueda de foco de la cuadrícula.
    if (tvNav != null) tvNav.rowOrder = emptyList()
    val gridState = rememberPosterGridState(profile)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = profile.continueWidth - 24.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(start = profile.edge, end = profile.edge, top = 4.dp, bottom = bottomSpace),
        horizontalArrangement = Arrangement.spacedBy(profile.itemGap),
        verticalArrangement = Arrangement.spacedBy(profile.itemGap)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                // En TV se vuelve con Atrás: un botón arriba solo sería otra parada del foco.
                if (!profile.isTv) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.home_back))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_continue_watching),
                        style = if (profile.isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pluralStringResource(R.plurals.home_titles_count, items.size, items.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = EntreBrand.TextDim
                    )
                }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.home_continue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EntreBrand.TextDim,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        itemsIndexed(items, key = { _, it -> it.key }, contentType = { _, _ -> "continue" }) { index, item ->
            val memoryKey = "continueAll|${item.key}"
            ContinueCard(
                profile = profile,
                item = item,
                fillWidth = true,
                modifier = focusMemory.modifierFor(memoryKey).then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                onFocused = {
                    focusMemory.onFocused(memoryKey)
                    onFocusItem(item)
                },
                onPlay = { onPlay(item) },
                onRemove = { onRemove(item) },
                onMenu = { onMenu(item) }
            )
        }
    }
}

@Composable
private fun remainingLabel(ms: Long): String {
    val minutes = ((ms + 59_999) / 60_000).toInt().coerceAtLeast(1)
    return if (minutes < 60) pluralStringResource(R.plurals.home_remaining_minutes, minutes, minutes)
    else stringResource(R.string.home_remaining_hours, minutes / 60, minutes % 60)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(
    profile: DeviceProfile,
    item: ContinueItem,
    modifier: Modifier,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMenu: () -> Unit,
    /** Cuadrícula "Ver todo": ocupa el ancho de su columna. */
    fillWidth: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFocusScale(focused, 1.05f, "continueScale")
    LaunchedEffect(focused) { if (focused) onFocused() }

    val shape = RoundedCornerShape(12.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = EntreBrand.Panel),
        border = if (focused) BorderStroke(3.dp, EntreBrand.Accent) else null,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(profile.continueWidth))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onLongClick = onMenu,
                onClick = onPlay
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (fillWidth) Modifier.aspectRatio(profile.continueWidth / profile.continueHeight)
                    else Modifier.height(profile.continueHeight)
                )
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
                    contentDescription = stringResource(R.string.home_resume),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            // OK mantenido: Compose lleva el foco hacia adentro de la tarjeta con cada repetición
            // de la tecla; si la X pudiera enfocarse, al soltar se quitaba el título. En TV se
            // quita desde el menú.
            if (!profile.isTv) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .focusProperties { canFocus = false }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.home_remove),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (item.fraction > 0f) {
                Text(
                    listOfNotNull(
                        stringResource(R.string.home_percent, (item.fraction * 100).toInt()).takeIf { item.fraction >= 0.01f },
                        item.remainingMs.takeIf { it > 0 }?.let { remainingLabel(it) }
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
            val subtitle = item.subtitle.asString()
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
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
    onSeeAll: () -> Unit,
    tvNav: TvCatalogNav? = null,
    navKey: String = ""
) {
    val rowState = rememberTvRowState(tvNav, navKey, profile.rowPrefetch)
    Column {
        // En TV "Ver todo" va como tarjeta al final de la fila: en el encabezado atraía el foco.
        RowHeader(row.label.asString(), profile, onSeeAll = if (profile.isTv) null else onSeeAll)
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = profile.edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(profile.itemGap)
        ) {
            itemsIndexed(row.items, key = { _, it -> it.key }, contentType = { _, it -> if (it is BrowseItem.Live) "live" else "poster" }) { index, item ->
                val memoryKey = "${row.title}|${item.key}"
                PosterCard(
                    profile = profile,
                    item = item,
                    fillWidth = false,
                    modifier = focusMemory.modifierFor(memoryKey).tvNavItem(tvNav, navKey, index),
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onFocused = {
                        focusMemory.onFocused(memoryKey)
                        onFocusItem(item)
                    }
                )
            }
            if (profile.isTv) {
                item(key = "seeAll", contentType = "seeAll") {
                    SeeAllCard(
                        profile = profile,
                        live = row.items.firstOrNull() is BrowseItem.Live,
                        onClick = onSeeAll,
                        modifier = Modifier.tvNavItem(tvNav, navKey, row.items.size)
                    )
                }
            }
        }
        // En TV también debajo de la fila: se llega bajando, sin recorrerla hasta el final.
        if (profile.isTv && tvNav != null) {
            SeeAllButton(
                onClick = onSeeAll,
                modifier = Modifier
                    .padding(start = profile.edge, top = 2.dp)
                    .tvNavItem(tvNav, navKey + TvCatalogNav.ALL_SUFFIX, 0)
            )
        }
    }
}

/**
 * Estado horizontal de una fila, registrado en [tvNav] para las flechas arriba/abajo. Cuando la
 * lista de filas arma por adelantado la fila siguiente, arma también sus primeros [prefetch]
 * pósters: las imágenes empiezan a bajar antes de llegar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberTvRowState(tvNav: TvCatalogNav?, navKey: String, prefetch: Int): LazyListState {
    val state = rememberLazyListState(prefetchStrategy = remember(prefetch) { LazyListPrefetchStrategy(nestedPrefetchItemCount = prefetch) })
    if (tvNav != null) {
        DisposableEffect(tvNav, navKey, state) {
            tvNav.registerRow(navKey, state)
            onDispose { tvNav.unregisterRow(navKey, state) }
        }
    }
    return state
}

/** "Ver todo" al final de la fila en TV, del alto de los pósters. */
@Composable
private fun SeeAllCard(profile: DeviceProfile, live: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFocusScale(focused, 1.08f, "seeAllScale")
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(if (live) profile.liveWidth else profile.posterWidth)
            .height(if (live) profile.liveHeight else profile.posterHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (focused) EntreBrand.Accent else EntreBrand.PanelHigh)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (focused) EntreBrand.OnAccent else Color.White,
                modifier = Modifier.size(36.dp)
            )
            Text(
                stringResource(R.string.home_see_all),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (focused) EntreBrand.OnAccent else Color.White
            )
        }
    }
}

@Composable
private fun RowHeader(title: String, profile: DeviceProfile, onSeeAll: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = profile.edge, end = profile.edge - 8.dp, top = if (profile.isTv) 4.dp else 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
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
private fun SeeAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(if (focused) EntreBrand.Accent else Color.Transparent)
            .border(1.dp, EntreBrand.Accent.copy(alpha = if (focused) 0f else 0.5f), shape)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
    ) {
        Text(
            stringResource(R.string.home_see_all),
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
    val scale by animateFocusScale(focused, 1.08f, "posterScale")
    LaunchedEffect(focused) { if (focused) onFocused() }
    val isLive = item is BrowseItem.Live
    val shape = RoundedCornerShape(12.dp)

    val outer = if (fillWidth) Modifier.fillMaxWidth()
    else Modifier.width(if (isLive) profile.liveWidth else profile.posterWidth)
    val imageBox = when {
        fillWidth && isLive -> Modifier.fillMaxWidth().aspectRatio(2f)
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
                .then(
                    if (isLive && !profile.lowRam) Modifier.background(LIVE_CARD_BRUSH)
                    else Modifier.background(EntreBrand.PanelHigh)
                )
                .then(if (focused) Modifier.border(3.dp, EntreBrand.Accent, shape) else Modifier)
                .then(
                    if (fillWidth && !isLive && !profile.lowRam) Modifier.onSizeChanged { GridPosterSize.px = it }
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl.isNotEmpty()) {
                val context = LocalContext.current
                var waiting by remember(item.imageUrl) { mutableStateOf(!isLive) }
                // Un póster que no llegó por la red se vuelve a pedir un par de veces; los rotos no.
                var retry by remember(item.imageUrl) { mutableIntStateOf(0) }
                var retryDue by remember(item.imageUrl) { mutableStateOf(false) }
                if (waiting) PosterPlaceholder(item.title)
                AsyncImage(
                    model = remember(item.imageUrl, retry) {
                        if (isLive) cardImage(item.imageUrl)
                        else posterRequest(context, item.imageUrl)
                            .apply { if (retry > 0) setParameter(POSTER_RETRY_PARAM, retry, memoryCacheKey = null) }
                            .build()
                    },
                    contentDescription = item.title,
                    modifier = if (isLive) Modifier.fillMaxSize().padding(8.dp) else Modifier.fillMaxSize(),
                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                    onState = if (isLive) null else { state ->
                        waiting = state !is AsyncImagePainter.State.Success
                        if (state is AsyncImagePainter.State.Error && retry < POSTER_RETRIES &&
                            ImageFailures.isTransient(state.result.throwable)
                        ) retryDue = true
                    }
                )
                if (retryDue) {
                    LaunchedEffect(retry) {
                        delay(POSTER_RETRY_DELAY_MS)
                        retryDue = false
                        retry++
                    }
                }
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
            if (rememberIsFavorite(item.key)) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            // Los canales de partidos traen nombres largos ("Liga MX 7:00 Guadalajara vs América").
            maxLines = if (isLive || !profile.isTv) 2 else 1,
            minLines = if (isLive) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )
    }
}

/** Renglón de la vista en lista: imagen chica y el nombre completo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListItemCard(
    item: BrowseItem,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val isLive = item is BrowseItem.Live
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) EntreBrand.PanelHigh else Color.Transparent)
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .size(width = if (isLive) 72.dp else 52.dp, height = if (isLive) 44.dp else 76.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EntreBrand.PanelHigh),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl.isNotEmpty()) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(item.imageUrl) {
                        if (isLive) cardImage(item.imageUrl) else posterRequest(context, item.imageUrl).build()
                    },
                    contentDescription = null,
                    modifier = if (isLive) Modifier.fillMaxSize().padding(4.dp) else Modifier.fillMaxSize(),
                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop
                )
            } else {
                Icon(iconFor(item), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            ratingText(item.rating)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = EntreBrand.TextDim)
            }
        }
        if (rememberIsFavorite(item.key)) FavoriteBadge(Modifier.padding(horizontal = 8.dp))
    }
}

/** Favoritos del perfil como estado: cada póster lee solo si el suyo está. */
val LocalFavoriteKeys = staticCompositionLocalOf<State<Set<String>>> { mutableStateOf(emptySet()) }

@Composable
private fun rememberIsFavorite(key: String): Boolean {
    val favorites = LocalFavoriteKeys.current
    return remember(key, favorites) { derivedStateOf { key in favorites.value } }.value
}

/** Corazón rojo pequeño sobre un círculo oscuro. */
@Composable
private fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
    ) {
        Icon(
            Icons.Default.Favorite,
            contentDescription = stringResource(R.string.home_in_my_list),
            tint = Color(0xFFE53935),
            modifier = Modifier.size(14.dp)
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

