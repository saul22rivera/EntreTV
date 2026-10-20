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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.entretv.app.ui.DeviceProfile
import com.entretv.app.ui.MY_LIST_ROW
import com.entretv.app.ui.Section
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
 *  - Celular: las secciones van arriba; la píldora flotante de abajo (Buscar, Categorías,
 *    Descargas) se esconde al bajar.
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
    onStrayStripFocus: () -> Unit = {}
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
                searchActive = searchActive,
                visible = pillVisible && !expanded,
                onSearch = onSearch,
                onMenu = { onExpandedChange(true) },
                onDownloads = onDownloads,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            IconStrip(
                isTv = profile.isTv,
                isUserMove = isUserMoveToStrip,
                onStrayFocus = onStrayStripFocus,
                section = section,
                searchActive = searchActive,
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
            ExpandedPanel(
                width = if (profile.isTv) 300.dp else 288.dp,
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
                onDownloads = onDownloads
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
            StripIcon(Icons.Default.Menu, "Abrir menú", selected = false, onClick = onExpand)
            Spacer(Modifier.height(8.dp))
        }
        StripIcon(Icons.Default.Search, "Buscar", selected = searchActive, modifier = expandOnFocus, onClick = onSearch)
        Spacer(Modifier.height(12.dp))
        Section.entries.forEach { s ->
            StripIcon(s.icon(), s.label, selected = !searchActive && s == section, modifier = expandOnFocus) { onSection(s) }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.weight(1f))
        StripIcon(Icons.Default.Download, "Descargas", selected = false, modifier = expandOnFocus, onClick = onDownloads)
        Spacer(Modifier.height(6.dp))
        StripIcon(Icons.Default.Settings, "Configuración", selected = false, modifier = expandOnFocus, onClick = onSettings)
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
    onDownloads: () -> Unit
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
                exit = { direction -> if (direction == FocusDirection.Left) FocusRequester.Cancel else FocusRequester.Default }
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
                    Icon(Icons.AutoMirrored.Filled.MenuOpen, contentDescription = "Cerrar menú", tint = EntreBrand.TextDim)
                }
            }
            PanelItem(
                icon = Icons.Default.Search,
                label = "Buscar",
                selected = searchActive,
                modifier = if (searchActive) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = onSearch
            )
            Section.entries.forEach { s ->
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
                    Text(
                        "CATEGORÍAS",
                        color = EntreBrand.Accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Organizar",
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
                    item(key = "__all") {
                        CategoryRow("Todas", categoryCounts.values.sum(), selectedCategory == null) { onCategory(null) }
                    }
                    item(key = "__mylist") {
                        CategoryRow(
                            label = MY_LIST_ROW,
                            count = myListCount,
                            selected = selectedCategory == MY_LIST_ROW,
                            icon = Icons.Default.Favorite
                        ) { onCategory(MY_LIST_ROW) }
                    }
                    items(categories, key = { it }) { c ->
                        CategoryRow(
                            label = categoryLabels[c] ?: c,
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
            PanelItem(icon = Icons.Default.Download, label = "Descargas", selected = false, onClick = onDownloads)
            PanelItem(icon = Icons.Default.Settings, label = "Configuración", selected = false, onClick = onSettings)
        }
    }
}

@Composable
private fun PanelItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
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
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
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
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
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
                contentDescription = if (locked) "Bloqueada" else null,
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
    searchActive: Boolean,
    visible: Boolean,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    onDownloads: () -> Unit,
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
            .padding(bottom = 14.dp)
            .graphicsLayer {
                translationY = hide * hiddenOffset
                alpha = 1f - hide
            }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            PillItem(Icons.Default.Search, "Buscar", searchActive, onSearch)
            PillItem(Icons.Default.Menu, "Categorías", false, onMenu)
            PillItem(Icons.Default.Download, "Descargas", false, onDownloads)
        }
    }
}

@Composable
private fun PillItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) EntreBrand.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val tint = if (selected) EntreBrand.OnAccent else Color.White.copy(alpha = 0.9f)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Secciones arriba en el celular (En vivo · Películas · Series); no tapan el catálogo. */
@Composable
fun SectionTabs(
    section: Section,
    searchActive: Boolean,
    onSection: (Section) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Section.entries.forEach { s ->
            val selected = !searchActive && s == section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.25f), RoundedCornerShape(50))
                    .clickable { onSection(s) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    s.label,
                    color = if (selected) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
