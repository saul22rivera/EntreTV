package com.entretv.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.entretv.app.R
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.repo.GuideProgram
import com.entretv.app.data.repo.GuideRepository
import com.entretv.app.ui.MY_LIST_ROW
import com.entretv.app.ui.SmallClock
import com.entretv.app.ui.theme.EntreBrand
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bloque de la grilla: un programa o un hueco sin datos (como mucho una hora, alineado a la hora). */
@Immutable
private data class GuideCell(val start: Long, val end: Long, val program: GuideProgram?)

private const val HALF_HOUR = 1800L
private const val HOUR = 3600L
/** Lo que entra a lo ancho: dos horas y media. */
private const val SPAN = 150 * 60L
/** Un programa que asoma menos que esto en un borde se trae a la vista. */
private const val MIN_VISIBLE = 20 * 60L
private const val MIN_OVERLAP_LEFT = 5 * 60L
/** Las filas componen la vista más este margen a cada lado, en tramos fijos. */
private const val BAND = 2 * 3600L
private val ROW_HEIGHT = 52.dp
private val BLOCK_GAP = 2.dp
private val CHANNEL_WIDTH = 168.dp

private fun floorHalfHour(t: Long) = t - Math.floorMod(t, HALF_HOUR)

/** Programas ordenados y huecos rellenados entre [from] y [to]: así siempre hay algo que enfocar. */
private fun buildCells(programs: List<GuideProgram>?, from: Long, to: Long): List<GuideCell> {
    val cells = ArrayList<GuideCell>()
    var t = from
    fun fill(until: Long) {
        while (t < until) {
            val next = minOf(until, t - Math.floorMod(t, HOUR) + HOUR)
            cells += GuideCell(t, next, null)
            t = next
        }
    }
    programs.orEmpty().forEach { p ->
        if (p.end <= t || p.start >= to) return@forEach
        // Guías con entradas superpuestas: lo que asoma después del programa anterior es ruido.
        if (p.start < t && p.end - t < MIN_OVERLAP_LEFT) return@forEach
        fill(p.start)
        val start = maxOf(p.start, t)
        cells += GuideCell(start, minOf(p.end, to), p)
        t = minOf(p.end, to)
    }
    fill(to)
    return cells
}

private fun indexAt(cells: List<GuideCell>, time: Long): Int {
    val i = cells.indexOfFirst { time >= it.start && time < it.end }
    return when {
        i >= 0 -> i
        cells.isEmpty() -> -1
        time < cells.first().start -> 0
        else -> cells.lastIndex
    }
}

/**
 * Guía en cuadrícula para TV: canales en filas, la hora a lo ancho (2 h 30 a la vista).
 * Un solo elemento enfocable maneja las flechas y dibuja el cursor: con cientos de bloques
 * no hay búsqueda de foco por geometría ni saltos raros, y se mantiene fluida en equipos chicos.
 */
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onPlayLive: (ChannelEntity) -> Unit,
    onPlayCatchUp: (ChannelEntity, GuideProgram) -> Unit,
    viewModel: GuideViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val category by viewModel.category.collectAsState()
    val channelState by viewModel.channels.collectAsState()
    val channels = channelState.list
    val programs by viewModel.programs.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val time: (Long) -> String = remember(timeFormat) { { seconds -> timeFormat.format(Date(seconds * 1000)) } }

    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    val anchor = rememberSaveable { floorHalfHour(System.currentTimeMillis() / 1000) }
    val archiveDays = remember(channels) { channels.maxOfOrNull { it.archiveDays } ?: 0 }
    // Con catch-up se puede ir hacia atrás hasta 3 días; si no, solo un poco antes de ahora.
    val windowStart = if (archiveDays > 0) anchor - minOf(archiveDays, 3) * GuideRepository.DAY_S else anchor - HOUR
    val windowEnd = anchor + GuideRepository.DAY_S

    var row by rememberSaveable { mutableIntStateOf(0) }
    var cursorTime by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    var viewStart by rememberSaveable { mutableLongStateOf(anchor - HALF_HOUR) }
    var shownCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val gridFocus = remember { FocusRequester() }
    val headerFocus = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }

    val currentViewStart = remember { { viewStart } }
    val bandStart = Math.floorDiv(viewStart, BAND) * BAND - BAND
    fun clampView(start: Long) = start.coerceIn(windowStart, maxOf(windowStart, windowEnd - SPAN))
    LaunchedEffect(windowStart) { viewStart = clampView(viewStart) }

    // Otra categoría: arriba de todo y en la hora actual.
    LaunchedEffect(category) {
        if (category != null && category != shownCategory) {
            if (shownCategory != null) {
                row = 0
                cursorTime = System.currentTimeMillis() / 1000
                viewStart = clampView(anchor - HALF_HOUR)
                listState.scrollToItem(0)
            }
            shownCategory = category
        }
    }
    LaunchedEffect(channels.size) { row = row.coerceIn(0, (channels.size - 1).coerceAtLeast(0)) }

    LaunchedEffect(channels.isNotEmpty()) {
        if (channels.isNotEmpty()) {
            delay(50)
            runCatching { gridFocus.requestFocus() }
        }
    }

    // Se piden las filas a la vista y unas cuantas más; al pasar rápido se espera a que se quede quieto.
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val visibleCount by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.size } }
    LaunchedEffect(channels, firstVisible, visibleCount, programs.loaded) {
        if (channels.isEmpty()) return@LaunchedEffect
        delay(150)
        val count = visibleCount.coerceAtLeast(8)
        val from = (firstVisible - 2).coerceAtLeast(0)
        val to = (firstVisible + count + 6).coerceAtMost(channels.size)
        val keepFrom = (firstVisible - 15).coerceAtLeast(0)
        val keepTo = (firstVisible + count + 15).coerceAtMost(channels.size)
        viewModel.requestVisible(
            channels.subList(from, to),
            channels.subList(keepFrom, keepTo).mapTo(HashSet()) { it.id }
        )
    }

    fun cellsOf(index: Int): List<GuideCell> =
        channels.getOrNull(index)?.let { buildCells(programs.byChannel[it.id], windowStart, windowEnd) }.orEmpty()

    fun reveal(target: Int) {
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val fully = visible.filter { it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset }
        val last = fully.lastOrNull()?.index ?: first
        val count = fully.size.coerceAtLeast(1)
        if (target < first || (target == first && visible.firstOrNull()?.offset?.let { it < 0 } == true)) {
            scope.launch { listState.scrollToItem(target) }
        } else if (target > last) {
            scope.launch { listState.scrollToItem((target - count + 1).coerceAtLeast(0)) }
        }
    }

    fun moveRow(delta: Int) {
        if (channels.isEmpty()) return
        val target = (row + delta).coerceIn(0, channels.lastIndex)
        if (target == row) return
        row = target
        reveal(target)
    }

    fun showCell(cell: GuideCell) {
        val viewEnd = viewStart + SPAN
        if (cell.start < viewStart && cell.end <= viewStart + MIN_VISIBLE) {
            viewStart = clampView(floorHalfHour(maxOf(cell.start, cell.end - SPAN + HALF_HOUR)))
        } else if (cell.start >= viewEnd - MIN_VISIBLE) {
            viewStart = clampView(floorHalfHour(cell.start) - HALF_HOUR)
        }
        cursorTime = maxOf(cell.start, viewStart)
    }

    fun moveCell(delta: Int) {
        val cells = cellsOf(row)
        val i = indexAt(cells, cursorTime)
        val target = cells.getOrNull(i + delta)
        if (target != null) showCell(target)
        else if (delta < 0 && viewStart > windowStart) viewStart = clampView(viewStart - HALF_HOUR)
    }

    fun shiftTime(seconds: Long) {
        viewStart = clampView(viewStart + seconds)
        cursorTime = (cursorTime + seconds).coerceIn(viewStart, viewStart + SPAN - 1)
    }

    fun activate() {
        val channel = channels.getOrNull(row) ?: return
        val cell = cellsOf(row).getOrNull(indexAt(cellsOf(row), cursorTime))
        val program = cell?.program
        if (program != null && program.archived && program.end <= System.currentTimeMillis() / 1000) {
            onPlayCatchUp(channel, program)
        } else {
            onPlayLive(channel)
        }
    }

    val selectedChannel = channels.getOrNull(row)
    val selectedCell = remember(selectedChannel, programs, cursorTime, windowStart) {
        cellsOf(row).let { it.getOrNull(indexAt(it, cursorTime)) }
    }

    Surface(color = EntreBrand.Ink, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)
        ) {
            // ---------------- Cabecera ----------------
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                Text(
                    stringResource(R.string.guide_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(16.dp))
                CategoryPicker(
                    categories = categories,
                    selected = category,
                    focusRequester = headerFocus,
                    onSelect = viewModel::selectCategory,
                    onDown = { runCatching { gridFocus.requestFocus() } },
                    onBack = onBack
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.guide_hint),
                    color = EntreBrand.TextDim,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                Spacer(Modifier.width(16.dp))
                SmallClock()
            }
            Spacer(Modifier.height(6.dp))

            // ---------------- Detalle del programa enfocado ----------------
            ProgramDetails(
                channel = selectedChannel,
                cell = selectedCell,
                now = now,
                loading = selectedChannel != null && selectedChannel.id !in programs.loaded,
                time = time
            )
            Spacer(Modifier.height(6.dp))

            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!channelState.settled) CircularProgressIndicator()
                    else Text(stringResource(R.string.live_no_channels), color = EntreBrand.TextDim)
                }
                return@Column
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val timelinePx = with(density) { (maxWidth - CHANNEL_WIDTH).toPx() }
                val pxPerSecond = timelinePx / SPAN
                val channelPx = with(density) { CHANNEL_WIDTH.toPx() }

                Column(Modifier.fillMaxSize()) {
                    TimeRuler(viewStart = viewStart, pxPerSecond = pxPerSecond, time = time)
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(gridFocus)
                            .onFocusChanged { gridFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                val down = event.type == KeyEventType.KeyDown
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        if (down) {
                                            if (row == 0) runCatching { headerFocus.requestFocus() } else moveRow(-1)
                                        }
                                        true
                                    }
                                    Key.DirectionDown -> { if (down) moveRow(1); true }
                                    Key.DirectionLeft -> { if (down) moveCell(-1); true }
                                    Key.DirectionRight -> { if (down) moveCell(1); true }
                                    Key.PageUp, Key.ChannelUp -> { if (down) moveRow(-visibleCount.coerceAtLeast(1)); true }
                                    Key.PageDown, Key.ChannelDown -> { if (down) moveRow(visibleCount.coerceAtLeast(1)); true }
                                    Key.MediaRewind -> { if (down) shiftTime(-2 * HOUR); true }
                                    Key.MediaFastForward -> { if (down) shiftTime(2 * HOUR); true }
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        if (down && event.nativeKeyEvent.repeatCount == 0) activate()
                                        true
                                    }
                                    Key.Back, Key.Escape, Key.Guide -> {
                                        if (!down) onBack()
                                        true
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                            val list = programs.byChannel[channel.id]
                            val cells = remember(list, windowStart, windowEnd) { buildCells(list, windowStart, windowEnd) }
                        GuideRow(
                            channel = channel,
                            cells = cells,
                            bandStart = bandStart,
                            bandEnd = bandStart + 2 * BAND + SPAN,
                            viewStart = currentViewStart,
                            pxPerSecond = pxPerSecond,
                                selectedStart = if (index == row && gridFocused) {
                                    cells.getOrNull(indexAt(cells, cursorTime))?.start
                                } else null,
                                rowSelected = index == row,
                                now = now,
                                loading = channel.id !in programs.loaded && list == null,
                                time = time
                            )
                        }
                    }
                }
                // Línea de la hora actual, sobre la regla y las filas.
                val nowX = (now - viewStart) * pxPerSecond
                if (nowX in 0f..timelinePx) {
                    Box(
                        Modifier
                            .offset { IntOffset((channelPx + nowX).roundToInt(), 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFFF5252))
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    categories: List<GuideCategory>,
    selected: String?,
    focusRequester: FocusRequester,
    onSelect: (String) -> Unit,
    onDown: () -> Unit,
    onBack: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val myList = stringResource(R.string.menu_my_list)
    fun label(c: GuideCategory) = if (c.raw == MY_LIST_ROW) myList else c.label
    val current = categories.firstOrNull { it.raw == selected }
    val index = categories.indexOfFirst { it.raw == selected }
    val shape = RoundedCornerShape(10.dp)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(if (focused) EntreBrand.Accent else Color.White.copy(alpha = 0.08f), shape)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        if (event.type == KeyEventType.KeyUp) onBack()
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent event.key in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionDown, Key.DirectionCenter, Key.Enter)
                    }
                    when (event.key) {
                        Key.DirectionLeft -> { categories.getOrNull(index - 1)?.let { onSelect(it.raw) }; true }
                        Key.DirectionRight -> { categories.getOrNull(index + 1)?.let { onSelect(it.raw) }; true }
                        Key.DirectionDown -> { onDown(); true }
                        Key.DirectionCenter, Key.Enter -> { open = true; true }
                        else -> false
                    }
                }
                .focusable(interactionSource = source)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                current?.let(::label).orEmpty(),
                color = if (focused) EntreBrand.OnAccent else Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.dp)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (focused) EntreBrand.OnAccent else Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text(label(c), fontWeight = if (c.raw == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onSelect(c.raw)
                        onDown()
                    }
                )
            }
        }
    }
}

@Composable
private fun ProgramDetails(
    channel: ChannelEntity?,
    cell: GuideCell?,
    now: Long,
    loading: Boolean,
    time: (Long) -> String
) {
    Row(Modifier.fillMaxWidth().height(96.dp)) {
        if (channel != null) {
            ChannelLogo(channel, Modifier.size(width = 72.dp, height = 48.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            val program = cell?.program
            Text(
                program?.title ?: stringResource(if (loading) R.string.live_guide_loading else R.string.guide_no_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (program != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    val status = when {
                        program.end <= now -> stringResource(R.string.guide_ended)
                        program.start <= now -> {
                            val left = ((program.end - now + 59) / 60).toInt()
                            pluralStringResource(R.plurals.guide_minutes_left, left, left)
                        }
                        else -> stringResource(R.string.guide_upcoming)
                    }
                    Text(
                        "${time(program.start)} – ${time(program.end)} · $status",
                        color = EntreBrand.TextDim,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (program.archived && program.end <= now) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Default.History, contentDescription = null, tint = EntreBrand.Accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.guide_catchup_available), color = EntreBrand.Accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (program.description.isNotBlank()) {
                    Text(
                        program.description,
                        color = EntreBrand.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (channel != null) {
                Text(channel.name, color = EntreBrand.TextDim, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TimeRuler(viewStart: Long, pxPerSecond: Float, time: (Long) -> String) {
    Row(Modifier.fillMaxWidth().height(24.dp)) {
        Spacer(Modifier.width(CHANNEL_WIDTH))
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            var t = floorHalfHour(viewStart)
            if (t < viewStart) t += HALF_HOUR
            while (t < viewStart + SPAN) {
                val x = ((t - viewStart) * pxPerSecond).roundToInt()
                Text(
                    time(t),
                    color = EntreBrand.TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.offset { IntOffset(x + 4, 0) }
                )
                Box(
                    Modifier
                        .offset { IntOffset(x, 0) }
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.18f))
                )
                t += HALF_HOUR
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: ChannelEntity,
    cells: List<GuideCell>,
    /** Tramo compuesto; cambia poco. Dentro de él, desplazar la hora solo reubica los bloques. */
    bandStart: Long,
    bandEnd: Long,
    viewStart: () -> Long,
    pxPerSecond: Float,
    selectedStart: Long?,
    rowSelected: Boolean,
    now: Long,
    loading: Boolean,
    time: (Long) -> String
) {
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(CHANNEL_WIDTH)
                .fillMaxHeight()
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (rowSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                .padding(horizontal = 6.dp)
        ) {
            ChannelLogo(channel, Modifier.size(width = 40.dp, height = 28.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                channel.name,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (rowSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (channel.archiveDays > 0) {
                Icon(Icons.Default.History, contentDescription = stringResource(R.string.guide_catchup_channel), tint = EntreBrand.TextDim, modifier = Modifier.size(12.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            cells.forEach { cell ->
                if (cell.end <= bandStart || cell.start >= bandEnd) return@forEach
                key(cell.start) {
                    GuideBlock(
                        cell = cell,
                        selected = selectedStart == cell.start,
                        now = now,
                        loading = loading,
                        time = time,
                        modifier = Modifier.timeSpan(cell.start, cell.end, viewStart, pxPerSecond)
                    )
                }
            }
        }
    }
}

/**
 * Ubica y recorta el bloque a la parte visible de [start, end): se lee la hora en la fase de
 * layout, así desplazar la vista no recompone las filas. Lo que queda afuera no se mide.
 */
private fun Modifier.timeSpan(start: Long, end: Long, viewStart: () -> Long, pxPerSecond: Float): Modifier =
    layout { measurable, constraints ->
        val view = viewStart()
        val from = maxOf(start, view)
        val to = minOf(end, view + SPAN)
        val width = ((to - from) * pxPerSecond).roundToInt() - BLOCK_GAP.roundToPx()
        if (width <= 0) return@layout layout(0, 0) {}
        val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(((from - view) * pxPerSecond).roundToInt(), 0)
        }
    }

@Composable
private fun GuideBlock(
    cell: GuideCell,
    selected: Boolean,
    now: Long,
    loading: Boolean,
    time: (Long) -> String,
    modifier: Modifier
) {
    val program = cell.program
    val past = cell.end <= now
    val live = cell.start <= now && now < cell.end
    val shape = RoundedCornerShape(6.dp)
    val background = when {
        selected -> EntreBrand.Accent
        program == null -> Color.White.copy(alpha = 0.03f)
        live -> Color.White.copy(alpha = 0.14f)
        past -> Color.White.copy(alpha = 0.05f)
        else -> Color.White.copy(alpha = 0.09f)
    }
    val content = when {
        selected -> EntreBrand.OnAccent
        program == null || (past && !program.archived) -> EntreBrand.TextDim.copy(alpha = 0.7f)
        else -> Color.White
    }
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .padding(horizontal = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (program != null && program.archived && past) {
                Icon(Icons.Default.History, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                program?.title ?: stringResource(if (loading) R.string.guide_loading else R.string.guide_no_info),
                color = content,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (program != null && (selected || live)) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (program != null) {
            Text(
                time(program.start),
                color = content.copy(alpha = 0.75f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChannelLogo(channel: ChannelEntity, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        if (channel.logoUrl.isNotBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp)
            )
        } else {
            Text(channel.name.take(2).uppercase(), color = EntreBrand.TextDim, fontSize = 11.sp)
        }
    }
}
