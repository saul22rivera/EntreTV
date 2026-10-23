package com.entretv.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.entretv.app.R
import com.entretv.app.ui.DeviceProfile
import com.entretv.app.ui.ALL_ROW
import com.entretv.app.ui.MY_LIST_ROW
import com.entretv.app.ui.Section
import com.entretv.app.ui.asString
import com.entretv.app.ui.label
import com.entretv.app.ui.rowLabel
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreWordmark

/** Ancho de la tira de íconos siempre visible en TV y tablet. */
val RAIL_COLLAPSED_WIDTH = 64.dp

/** Espacio que deja abajo la píldora flotante del celular. */
val PILL_SPACE = 96.dp

fun Section.icon(): ImageVector = when (this) {
    Section.LIVE -> Icons.Default.LiveTv
    Section.MOVIES -> Icons.Default.Movie
    Section.SERIES -> Icons.Default.VideoLibrary
}

/**
 * Navegación superpuesta: nunca cambia el tamaño del catálogo, así que abrir o cerrar
 * no reacomoda los pósters. Todo se anima con traslación y transparencia.
 *
 *  - TV: la tira de íconos se expande al recibir el foco y se cierra al salir.
 *  - Tablet: tocar el ícono de menú o deslizar sobre la tira la expande.
 *  - Celular: la píldora flotante de abajo (Inicio, secciones y Categorías) se esconde al
 *    bajar; las categorías de la sección van arriba como chips y en el panel de Categorías.
 */
@Composable
fun NavRail(
    profile: DeviceProfile,
    section: Section,
    searchActive: Boolean,
    categories: List<String>,
    categoryCounts: Map<String, Int>,
    categoryLabels: Map<String, String>,
    lockedCategories: Set<String>,
    selectedCategory: String?,
    showCategories: Boolean,
    myListCount: Int,
    expanded: Boolean,
    pillVisible: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onSection: (Section) -> Unit,
    onCategory: (String?) -> Unit,
    onCategoryMenu: (String) -> Unit,
    onOrganize: () -> Unit,
    onSettings: () -> Unit,
    onDownloads: () -> Unit,
    isUserMoveToStrip: () -> Boolean = { true },
    onStrayStripFocus: () -> Unit = {},
    summaryActive: Boolean = false,
    onHome: () -> Unit = {},
    /** TV: secciones sin panel de categorías (En vivo las trae en pantalla); abren el menú principal. */
    tvMenuOnlySections: Set<Section> = emptySet(),
    /** "Perfil: …" en el menú; null = sin perfiles. */
    profileLabel: String? = null,
    profileTint: Color? = null,
    onProfile: () -> Unit = {}
) {
    val phone = profile.compactRail
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = expanded, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { onExpandedChange(false) } }
            )
        }

        if (phone) {
            FloatingPill(
                section = section,
                summaryActive = summaryActive,
                searchActive = searchActive,
                visible = pillVisible && !expanded,
                onHome = onHome,
                onSection = onSection,
                onMenu = { onExpandedChange(true) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            IconStrip(
                isTv = profile.isTv,
                isUserMove = isUserMoveToStrip,
                onStrayFocus = onStrayStripFocus,
                section = section,
                searchActive = searchActive,
                summaryActive = summaryActive,
                onHome = onHome,
                onExpand = { onExpandedChange(true) },
                onSearch = onSearch,
                onSection = onSection,
                onSettings = onSettings,
                onDownloads = onDownloads,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(160))
        ) {
            if (profile.isTv) TvExpandedPanel(
                menuOnlySections = tvMenuOnlySections,
                section = section,
                searchActive = searchActive,
                summaryActive = summaryActive,
                onHome = { onExpandedChange(false); onHome() },
                categories = categories,
                categoryCounts = categoryCounts,
                categoryLabels = categoryLabels,
                lockedCategories = lockedCategories,
                selectedCategory = selectedCategory,
                showCategories = showCategories,
                myListCount = myListCount,
                onCollapse = { onExpandedChange(false) },
                onSearch = { onExpandedChange(false); onSearch() },
                onSection = onSection,
                onCategory = { onExpandedChange(false); onCategory(it) },
                onCategoryMenu = { onExpandedChange(false); onCategoryMenu(it) },
                onOrganize = { onExpandedChange(false); onOrganize() },
                onSettings = { onExpandedChange(false); onSettings() },
                onDownloads = onDownloads,
                profileLabel = profileLabel,
                profileTint = profileTint,
                onProfile = { onExpandedChange(false); onProfile() }
            ) else ExpandedPanel(
                width = 288.dp,
                // En el celular las secciones ya están en la píldora: el panel es de la sección elegida.
                showSections = !phone,
                section = section,
                searchActive = searchActive,
                categories = categories,
                categoryCounts = categoryCounts,
                categoryLabels = categoryLabels,
                lockedCategories = lockedCategories,
                selectedCategory = selectedCategory,
                showCategories = showCategories,
                myListCount = myListCount,
                onCollapse = { onExpandedChange(false) },
                onSearch = { onExpandedChange(false); onSearch() },
                onSection = onSection,
                onCategory = { onExpandedChange(false); onCategory(it) },
                onCategoryMenu = { onExpandedChange(false); onCategoryMenu(it) },
                onOrganize = { onExpandedChange(false); onOrganize() },
                onSettings = { onExpandedChange(false); onSettings() },
                onDownloads = onDownloads,
                profileLabel = profileLabel,
                profileTint = profileTint,
                onProfile = { onExpandedChange(false); onProfile() }
            )
        }
    }
}

@Composable
private fun IconStrip(
    isTv: Boolean,
    isUserMove: () -> Boolean,
    onStrayFocus: () -> Unit,
    section: Section,
    searchActive: Boolean,
    summaryActive: Boolean,
    onHome: () -> Unit,
    onExpand: () -> Unit,
    onSearch: () -> Unit,
    onSection: (Section) -> Unit,
    onSettings: () -> Unit,
    onDownloads: () -> Unit,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val swipePx = with(density) { 24.dp.toPx() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(RAIL_COLLAPSED_WIDTH)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(EntreBrand.Ink, EntreBrand.Ink.copy(alpha = 0.85f))))
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { change, amount ->
                        total += amount
                        if (total > swipePx) {
                            change.consume()
                            total = Float.NEGATIVE_INFINITY
                            onExpand()
                        }
                    }
                )
            }
            .padding(vertical = 12.dp)
    ) {
        // En TV basta con llegar con el foco; en tablet este botón abre el panel. El foco que el sistema
        // deja aquí al cerrarse el panel vuelve al catálogo en lugar de reabrirlo.
        val expandOnFocus: Modifier = if (isTv) Modifier
            .onFocusChanged {
                if (it.isFocused) {
                    if (isUserMove()) onExpand() else onStrayFocus()
                }
            } else Modifier
        if (!isTv) {
            StripIcon(Icons.Default.Menu, stringResource(R.string.menu_open), selected = false, onClick = onExpand)
            Spacer(Modifier.height(8.dp))
        }
        StripIcon(Icons.Default.Search, stringResource(R.string.menu_search), selected = searchActive, modifier = expandOnFocus, onClick = onSearch)
        Spacer(Modifier.height(12.dp))
        if (isTv) {
            StripIcon(Icons.Default.Home, stringResource(R.string.menu_home), selected = summaryActive && !searchActive, modifier = expandOnFocus, onClick = onHome)
            Spacer(Modifier.height(6.dp))
        }
        Section.entries.forEach { s ->
            StripIcon(s.icon(), s.label, selected = !searchActive && !summaryActive && s == section, modifier = expandOnFocus) { onSection(s) }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.weight(1f))
        StripIcon(Icons.Default.Download, stringResource(R.string.menu_downloads), selected = false, modifier = expandOnFocus, onClick = onDownloads)
        Spacer(Modifier.height(6.dp))
        StripIcon(Icons.Default.Settings, stringResource(R.string.menu_settings), selected = false, modifier = expandOnFocus, onClick = onSettings)
    }
}

@Composable
private fun StripIcon(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> EntreBrand.Accent
                    focused -> Color.White.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) EntreBrand.OnAccent else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExpandedPanel(
    width: androidx.compose.ui.unit.Dp,
    showSections: Boolean,
    section: Section,
    searchActive: Boolean,
    categories: List<String>,
    categoryCounts: Map<String, Int>,
    categoryLabels: Map<String, String>,
    lockedCategories: Set<String>,
    selectedCategory: String?,
    showCategories: Boolean,
    myListCount: Int,
    onCollapse: () -> Unit,
    onSearch: () -> Unit,
    onSection: (Section) -> Unit,
    onCategory: (String?) -> Unit,
    onCategoryMenu: (String) -> Unit,
    onOrganize: () -> Unit,
    onSettings: () -> Unit,
    onDownloads: () -> Unit,
    profileLabel: String?,
    profileTint: Color?,
    onProfile: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Surface(
        color = EntreBrand.Panel.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        shadowElevation = 24.dp,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .pointerInput(Unit) { detectTapGestures { } }
            .onFocusChanged {
                if (it.hasFocus) hadFocus = true
                else if (hadFocus) {
                    hadFocus = false
                    onCollapse()
                }
            }
            // La flecha izquierda no sale del panel: del otro lado está la tira de íconos, que lo
            // volvería a abrir. Se sale con derecha, Atrás o "Cerrar menú".
            .focusProperties {
                onExit = { if (requestedFocusDirection == FocusDirection.Left) cancelFocusChange() }
            }
            .focusGroup()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 14.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)) {
                EntreWordmark()
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCollapse)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuOpen, contentDescription = stringResource(R.string.menu_close), tint = EntreBrand.TextDim)
                }
            }
            PanelItem(
                icon = Icons.Default.Search,
                label = stringResource(R.string.menu_search),
                selected = searchActive,
                modifier = if (searchActive) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = onSearch
            )
            if (showSections) Section.entries.forEach { s ->
                val selected = !searchActive && s == section
                PanelItem(
                    icon = s.icon(),
                    label = s.label,
                    selected = selected,
                    modifier = if (selected) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onSection(s) }
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))
            if (showCategories && categories.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                    if (!showSections) {
                        Icon(section.icon(), contentDescription = null, tint = EntreBrand.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (showSections) stringResource(R.string.menu_categories_header) else section.label.uppercase(),
                        color = EntreBrand.Accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.menu_organize),
                        color = EntreBrand.TextDim,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOrganize)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    item(key = "__home") {
                        CategoryRow(stringResource(R.string.menu_featured), 0, selectedCategory == null, icon = Icons.Default.Home) { onCategory(null) }
                    }
                    item(key = "__all") {
                        CategoryRow(stringResource(R.string.menu_all), categoryCounts.values.sum(), selectedCategory == ALL_ROW) { onCategory(ALL_ROW) }
                    }
                    item(key = "__mylist") {
                        CategoryRow(
                            label = stringResource(R.string.menu_my_list),
                            count = myListCount,
                            selected = selectedCategory == MY_LIST_ROW,
                            icon = Icons.Default.Favorite
                        ) { onCategory(MY_LIST_ROW) }
                    }
                    items(categories, key = { it }) { c ->
                        CategoryRow(
                            label = categoryLabels[c] ?: rowLabel(c).asString(),
                            count = categoryCounts[c] ?: 0,
                            selected = c == selectedCategory,
                            locked = c in lockedCategories,
                            onMenu = { onCategoryMenu(c) }
                        ) { onCategory(c) }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
            if (profileLabel != null) {
                PanelItem(icon = Icons.Default.AccountCircle, label = profileLabel, selected = false, iconTint = profileTint, onClick = onProfile)
            }
            PanelItem(icon = Icons.Default.Download, label = stringResource(R.string.menu_downloads), selected = false, onClick = onDownloads)
            PanelItem(icon = Icons.Default.Settings, label = stringResource(R.string.menu_settings), selected = false, onClick = onSettings)
        }
    }
}

private enum class TvLevel { CATEGORIES, MENU }

/**
 * Panel de TV en dos niveles para no amontonar todo: la izquierda desde el catálogo abre el
 * menú principal (Buscar, Inicio, secciones, Descargas y Configuración) y la derecha vuelve
 * siempre al contenido; sobre otra sección, la abre. OK sobre una sección (o "Categorías")
 * abre sus categorías con el foco en "Todas"; izquierda o Atrás desde ahí vuelve al menú.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvExpandedPanel(
    menuOnlySections: Set<Section>,
    section: Section,
    searchActive: Boolean,
    summaryActive: Boolean,
    onHome: () -> Unit,
    categories: List<String>,
    categoryCounts: Map<String, Int>,
    categoryLabels: Map<String, String>,
    lockedCategories: Set<String>,
    selectedCategory: String?,
    showCategories: Boolean,
    myListCount: Int,
    onCollapse: () -> Unit,
    onSearch: () -> Unit,
    onSection: (Section) -> Unit,
    onCategory: (String?) -> Unit,
    onCategoryMenu: (String) -> Unit,
    onOrganize: () -> Unit,
    onSettings: () -> Unit,
    onDownloads: () -> Unit,
    profileLabel: String?,
    profileTint: Color?,
    onProfile: () -> Unit
) {
    val hasCategories = showCategories && !summaryActive && section !in menuOnlySections
    var level by remember { mutableStateOf(TvLevel.MENU) }
    var focusAll by remember { mutableStateOf(false) }
    // Mientras cambia de nivel el foco pasa por el botón de cerrar: si quedara en un elemento
    // que desaparece, el panel creería que el foco salió y se cerraría.
    var switching by remember { mutableStateOf(true) }
    var hadFocus by remember { mutableStateOf(false) }
    val anchor = remember { FocusRequester() }
    val levelFocus = remember { FocusRequester() }

    LaunchedEffect(level) {
        // Dos frames: la lista de categorías se desplaza al elemento antes de enfocarlo.
        withFrameNanos { }
        withFrameNanos { }
        runCatching { levelFocus.requestFocus() }
        withFrameNanos { }
        switching = false
    }

    fun go(to: TvLevel) {
        if (level == to) return
        switching = true
        runCatching { anchor.requestFocus() }
        level = to
    }

    var focusedSection by remember { mutableStateOf<Section?>(null) }
    var homeFocused by remember { mutableStateOf(false) }

    fun openSection(s: Section) {
        onSection(s)
        if (showCategories && s !in menuOnlySections) {
            focusAll = true
            go(TvLevel.CATEGORIES)
        } else onCollapse()
    }

    /** Derecha en el menú: al contenido de lo enfocado; si es lo que ya se ve, solo cierra. */
    fun goRight() {
        val target = focusedSection
        when {
            homeFocused && (!summaryActive || searchActive) -> onHome()
            target != null && (target != section || summaryActive || searchActive) -> {
                onSection(target)
                onCollapse()
            }
            else -> onCollapse()
        }
    }

    Surface(
        color = EntreBrand.Panel.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        shadowElevation = 24.dp,
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .pointerInput(Unit) { detectTapGestures { } }
            .onFocusChanged {
                if (it.hasFocus) hadFocus = true
                else if (hadFocus && !switching) {
                    hadFocus = false
                    onCollapse()
                }
            }
            .focusProperties {
                onExit = { if (requestedFocusDirection == FocusDirection.Left) cancelFocusChange() }
            }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (down && level == TvLevel.CATEGORIES) go(TvLevel.MENU)
                        true
                    }
                    // Derecha nunca se deja a la búsqueda por geometría: mandaba el foco al campo
                    // de búsqueda o a ningún lado. El catálogo recupera su último póster al cerrar.
                    Key.DirectionRight -> {
                        if (down) {
                            if (level == TvLevel.MENU) goRight() else onCollapse()
                        }
                        true
                    }
                    Key.Back, Key.Escape -> {
                        if (down) {
                            if (level == TvLevel.CATEGORIES) go(TvLevel.MENU) else onCollapse()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 14.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)) {
                EntreWordmark()
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .focusRequester(anchor)
                        .clickable(onClick = onCollapse)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuOpen, contentDescription = stringResource(R.string.menu_close), tint = EntreBrand.TextDim)
                }
            }
            PanelItem(
                icon = Icons.Default.Search,
                label = stringResource(R.string.menu_search),
                selected = searchActive,
                modifier = if (level == TvLevel.MENU && searchActive) Modifier.focusRequester(levelFocus) else Modifier,
                onClick = onSearch
            )
            when (level) {
                TvLevel.MENU -> {
                    val homeSelected = summaryActive && !searchActive
                    PanelItem(
                        icon = Icons.Default.Home,
                        label = stringResource(R.string.menu_home),
                        selected = homeSelected,
                        modifier = (if (homeSelected) Modifier.focusRequester(levelFocus) else Modifier)
                            .onFocusChanged { homeFocused = it.isFocused },
                        onClick = onHome
                    )
                    Section.entries.forEach { s ->
                        val selected = !searchActive && !summaryActive && s == section
                        PanelItem(
                            icon = s.icon(),
                            label = s.label,
                            selected = selected,
                            modifier = (if (selected) Modifier.focusRequester(levelFocus) else Modifier)
                                .onFocusChanged {
                                    if (it.isFocused) focusedSection = s
                                    else if (focusedSection == s) focusedSection = null
                                },
                            onClick = { openSection(s) }
                        )
                    }
                    if (hasCategories) {
                        PanelItem(
                            icon = Icons.Default.Menu,
                            label = stringResource(R.string.menu_categories),
                            selected = false,
                            onClick = {
                                focusAll = false
                                go(TvLevel.CATEGORIES)
                            }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                    if (profileLabel != null) {
                        PanelItem(icon = Icons.Default.AccountCircle, label = profileLabel, selected = false, iconTint = profileTint, onClick = onProfile)
                    }
                    PanelItem(icon = Icons.Default.Download, label = stringResource(R.string.menu_downloads), selected = false, onClick = onDownloads)
                    PanelItem(icon = Icons.Default.Settings, label = stringResource(R.string.menu_settings), selected = false, onClick = onSettings)
                }
                TvLevel.CATEGORIES -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp)
                    ) {
                        Icon(section.icon(), contentDescription = null, tint = EntreBrand.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            section.label.uppercase(),
                            color = EntreBrand.Accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.menu_organize),
                            color = EntreBrand.TextDim,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOrganize)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    val focusTarget = when {
                        focusAll -> ALL_ROW
                        selectedCategory == null -> null
                        selectedCategory == ALL_ROW || selectedCategory == MY_LIST_ROW || selectedCategory in categories -> selectedCategory
                        else -> null
                    }
                    val listState = rememberLazyListState()
                    LaunchedEffect(Unit) {
                        val index = when (focusTarget) {
                            null, ALL_ROW, MY_LIST_ROW -> 0
                            else -> categories.indexOf(focusTarget) + 3
                        }
                        if (index > 0) listState.scrollToItem(index)
                    }
                    fun Modifier.target(name: String?): Modifier =
                        if (name == focusTarget) this.focusRequester(levelFocus) else this
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        item(key = "__home") {
                            CategoryRow(
                                stringResource(R.string.menu_featured), 0, selectedCategory == null, icon = Icons.Default.Home,
                                modifier = Modifier.target(null)
                            ) { onCategory(null) }
                        }
                        item(key = "__all") {
                            CategoryRow(
                                stringResource(R.string.menu_all), categoryCounts.values.sum(), selectedCategory == ALL_ROW,
                                modifier = Modifier.target(ALL_ROW)
                            ) { onCategory(ALL_ROW) }
                        }
                        item(key = "__mylist") {
                            CategoryRow(
                                label = stringResource(R.string.menu_my_list),
                                count = myListCount,
                                selected = selectedCategory == MY_LIST_ROW,
                                icon = Icons.Default.Favorite,
                                modifier = Modifier.target(MY_LIST_ROW)
                            ) { onCategory(MY_LIST_ROW) }
                        }
                        items(categories, key = { it }) { c ->
                            CategoryRow(
                                label = categoryLabels[c] ?: rowLabel(c).asString(),
                                count = categoryCounts[c] ?: 0,
                                selected = c == selectedCategory,
                                locked = c in lockedCategories,
                                onMenu = { onCategoryMenu(c) },
                                modifier = Modifier.target(c)
                            ) { onCategory(c) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(
                when {
                    selected -> EntreBrand.Accent
                    focused -> Color.White.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        val tint = if (selected) EntreBrand.OnAccent else Color.White
        Icon(icon, contentDescription = null, tint = iconTint ?: tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    label: String,
    count: Int,
    selected: Boolean,
    locked: Boolean = false,
    icon: ImageVector? = null,
    onMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.14f)
                    selected -> EntreBrand.Accent.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            // Tecla Menú del control: opciones de la categoría.
            .onPreviewKeyEvent { event ->
                if (onMenu != null && event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                    onMenu()
                    true
                } else false
            }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onLongClick = onMenu,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        if (locked || icon != null) {
            Icon(
                icon ?: Icons.Default.Lock,
                contentDescription = if (locked) stringResource(R.string.menu_locked) else null,
                tint = if (icon != null) EntreBrand.Accent else EntreBrand.TextDim,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = if (selected) EntreBrand.Accent else Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text(
                count.toString(),
                color = EntreBrand.TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun FloatingPill(
    section: Section,
    summaryActive: Boolean,
    searchActive: Boolean,
    visible: Boolean,
    onHome: () -> Unit,
    onSection: (Section) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val hiddenOffset = with(density) { 120.dp.toPx() }
    val hide by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "pill"
    )
    Surface(
        color = EntreBrand.PanelHigh.copy(alpha = 0.96f),
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 16.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationY = hide * hiddenOffset
                alpha = 1f - hide
            }
    ) {
        // Botones con ícono arriba y texto abajo. Buscar va en la lupa de arriba; Categorías
        // solo dentro de una sección, porque Inicio no tiene categorías.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            PillItem(Icons.Default.Home, stringResource(R.string.menu_home), summaryActive && !searchActive, onHome)
            Section.entries.forEach { s ->
                PillItem(s.icon(), s.label, !summaryActive && !searchActive && s == section) { onSection(s) }
            }
            if (!summaryActive) PillItem(Icons.Default.Menu, stringResource(R.string.menu_categories), false, onMenu)
        }
    }
}

@Composable
private fun RowScope.PillItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) EntreBrand.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
    ) {
        val tint = if (selected) EntreBrand.OnAccent else Color.White.copy(alpha = 0.9f)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * Categorías de la sección arriba en el celular ("Destacados", "Todas", "Mi lista" y las del servidor);
 * se deslizan a la derecha y la elegida queda a la vista.
 */
@Composable
fun CategoryChips(
    categories: List<String>,
    labels: Map<String, String>,
    locked: Set<String>,
    myListCount: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val entries: List<String?> = remember(categories, myListCount) {
        buildList {
            add(null)
            add(ALL_ROW)
            if (myListCount > 0) add(MY_LIST_ROW)
            addAll(categories.filter { it != MY_LIST_ROW && it != ALL_ROW })
        }
    }
    val state = rememberLazyListState()
    LaunchedEffect(selected, entries) {
        val index = entries.indexOf(selected)
        if (index >= 0) state.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        itemsIndexed(entries, key = { index, name -> "$index|$name" }) { _, name ->
            val isSelected = name == selected
            val label = when (name) {
                null -> stringResource(R.string.menu_featured)
                ALL_ROW -> stringResource(R.string.menu_all)
                MY_LIST_ROW -> stringResource(R.string.menu_my_list_count, myListCount)
                else -> labels[name] ?: name
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = if (isSelected) 0f else 0.25f), RoundedCornerShape(50))
                    .clickable { onSelect(name) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                val tint = if (isSelected) Color.Black else Color.White
                val icon = when {
                    name == MY_LIST_ROW -> Icons.Default.Favorite
                    name in locked -> Icons.Default.Lock
                    else -> null
                }
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
