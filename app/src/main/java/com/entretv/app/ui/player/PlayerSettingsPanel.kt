package com.entretv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Tracks
import com.entretv.app.data.settings.PlayerPrefs
import com.entretv.app.data.settings.SUBTITLES_OFF
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.player.TechInfo
import com.entretv.app.ui.theme.EntreBrand
import kotlinx.coroutines.delay

/** Lo que el panel puede cambiar; lo implementa el PlayerViewModel. */
interface PlayerSettingsActions {
    fun selectAudio(option: TrackOption)
    /** null = subtítulos desactivados. */
    fun selectSubtitle(option: TrackOption?)
    /** null = calidad automática. */
    fun selectQuality(option: TrackOption?)
    fun setSubtitleSize(size: Int)
    fun setResizeMode(mode: Int)
    fun setSpeed(speed: Float)
    /** 0 = apagado, -1 = al terminar el contenido. */
    fun setSleepTimer(minutes: Int)
}

const val SLEEP_AT_END = -1

private enum class Page(val title: String) {
    MAIN("Ajustes"),
    AUDIO("Audio"),
    SUBTITLES("Subtítulos"),
    QUALITY("Calidad"),
    SCREEN("Pantalla"),
    SPEED("Velocidad"),
    SLEEP("Temporizador"),
    INFO("Información técnica")
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val SLEEP_OPTIONS = listOf(15, 30, 60)

fun resizeLabel(mode: Int): String = when (mode) {
    SettingsRepository.RESIZE_ZOOM -> "Rellenar"
    SettingsRepository.RESIZE_FILL -> "Estirar"
    else -> "Ajustar"
}

private fun speedLabel(speed: Float): String =
    if (speed == 1f) "Normal" else "${speed.toString().removeSuffix(".0")}x"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerSettingsPanel(
    visible: Boolean,
    tracks: Tracks,
    prefs: PlayerPrefs,
    speed: Float,
    isLive: Boolean,
    sleepMinutes: Int,
    sleepEndsAt: Long,
    techInfo: () -> TechInfo,
    actions: PlayerSettingsActions,
    onDismiss: () -> Unit
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var page by remember { mutableStateOf(Page.MAIN) }
    LaunchedEffect(visible) { if (visible) page = Page.MAIN }
    BackHandler(enabled = visible) { if (page != Page.MAIN) page = Page.MAIN else onDismiss() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter),
            enter = if (wide) slideInHorizontally { it } + fadeIn() else slideInVertically { it } + fadeIn(),
            exit = if (wide) slideOutHorizontally { it } + fadeOut() else slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                color = EntreBrand.Panel.copy(alpha = 0.97f),
                shape = if (wide) RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
                else RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = (if (wide) Modifier.fillMaxHeight().width(380.dp) else Modifier.fillMaxWidth().heightIn(max = 520.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .focusProperties { exit = { FocusRequester.Cancel } }
                    .focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && page != Page.MAIN) {
                            page = Page.MAIN
                            true
                        } else false
                    }
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState != Page.MAIN
                        (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                    },
                    label = "settingsPage"
                ) { current ->
                    PageContent(
                        page = current,
                        tracks = tracks,
                        prefs = prefs,
                        speed = speed,
                        isLive = isLive,
                        sleepMinutes = sleepMinutes,
                        sleepEndsAt = sleepEndsAt,
                        techInfo = techInfo,
                        actions = actions,
                        onNavigate = { page = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageContent(
    page: Page,
    tracks: Tracks,
    prefs: PlayerPrefs,
    speed: Float,
    isLive: Boolean,
    sleepMinutes: Int,
    sleepEndsAt: Long,
    techInfo: () -> TechInfo,
    actions: PlayerSettingsActions,
    onNavigate: (Page) -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val audio = tracks.audioOptions()
    val subtitles = tracks.subtitleOptions()
    val qualities = tracks.qualityOptions()
    val back = { onNavigate(Page.MAIN) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        when (page) {
            Page.MAIN -> {
                item { PanelTitle("Ajustes de reproducción") }
                item {
                    PanelRow(
                        title = "Audio",
                        value = audio.firstOrNull { it.selected }?.label ?: if (audio.isEmpty()) "Sin pistas" else "Automático",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        chevron = true,
                        modifier = Modifier.focusRequester(focus),
                        onClick = { onNavigate(Page.AUDIO) }
                    )
                }
                item {
                    PanelRow(
                        title = "Subtítulos",
                        value = when {
                            subtitles.isEmpty() -> "No disponibles"
                            else -> subtitles.firstOrNull { it.selected }?.label ?: "Desactivados"
                        },
                        icon = Icons.Default.ClosedCaption,
                        chevron = true,
                        onClick = { onNavigate(Page.SUBTITLES) }
                    )
                }
                if (qualities.size > 1) {
                    item {
                        val current = qualities.firstOrNull { it.selected }
                        PanelRow(
                            title = "Calidad",
                            value = if (tracks.hasFixedQuality() && current != null) current.label
                            else "Automática" + (current?.let { " · ${it.format.height}p" } ?: ""),
                            icon = Icons.Default.HighQuality,
                            chevron = true,
                            onClick = { onNavigate(Page.QUALITY) }
                        )
                    }
                }
                item {
                    PanelRow(
                        title = "Pantalla",
                        value = resizeLabel(prefs.resizeMode),
                        icon = Icons.Default.AspectRatio,
                        chevron = true,
                        onClick = { onNavigate(Page.SCREEN) }
                    )
                }
                if (!isLive) {
                    item {
                        PanelRow(
                            title = "Velocidad",
                            value = speedLabel(speed),
                            icon = Icons.Default.Speed,
                            chevron = true,
                            onClick = { onNavigate(Page.SPEED) }
                        )
                    }
                }
                item {
                    PanelRow(
                        title = "Temporizador",
                        value = sleepLabel(sleepMinutes, sleepEndsAt),
                        icon = Icons.Default.Bedtime,
                        chevron = true,
                        onClick = { onNavigate(Page.SLEEP) }
                    )
                }
                item {
                    PanelRow(
                        title = "Información técnica",
                        icon = Icons.Default.Info,
                        chevron = true,
                        onClick = { onNavigate(Page.INFO) }
                    )
                }
            }

            Page.AUDIO -> {
                backHeader(page, back)
                if (audio.isEmpty()) item { PanelHint("Este contenido no informa pistas de audio.") }
                val initial = audio.indexOfFirst { it.selected }.coerceAtLeast(0)
                audio.forEachIndexed { i, option ->
                    item {
                        PanelRow(
                            title = option.label,
                            selected = option.selected,
                            modifier = if (i == initial) Modifier.focusRequester(focus) else Modifier,
                            onClick = { actions.selectAudio(option); back() }
                        )
                    }
                }
            }

            Page.SUBTITLES -> {
                backHeader(page, back)
                val anySelected = subtitles.any { it.selected }
                item {
                    PanelRow(
                        title = "Desactivados",
                        selected = !anySelected,
                        modifier = if (!anySelected) Modifier.focusRequester(focus) else Modifier,
                        onClick = { actions.selectSubtitle(null) }
                    )
                }
                subtitles.forEach { option ->
                    item {
                        PanelRow(
                            title = option.label,
                            selected = option.selected,
                            modifier = if (option.selected) Modifier.focusRequester(focus) else Modifier,
                            onClick = { actions.selectSubtitle(option) }
                        )
                    }
                }
                if (subtitles.isEmpty()) item { PanelHint("Este contenido no trae subtítulos.") }
                item { PanelSection("Tamaño del texto") }
                listOf(
                    SettingsRepository.SUBTITLE_SMALL to "Pequeño",
                    SettingsRepository.SUBTITLE_NORMAL to "Normal",
                    SettingsRepository.SUBTITLE_LARGE to "Grande"
                ).forEach { (size, label) ->
                    item {
                        PanelRow(
                            title = label,
                            selected = prefs.subtitleSize == size,
                            onClick = { actions.setSubtitleSize(size) }
                        )
                    }
                }
                if (prefs.subtitleLanguage.isNotEmpty() && prefs.subtitleLanguage != SUBTITLES_OFF) {
                    item { PanelHint("Se activarán solos en ${languageName(prefs.subtitleLanguage)} cuando estén disponibles.") }
                }
            }

            Page.QUALITY -> {
                backHeader(page, back)
                val fixed = tracks.hasFixedQuality()
                item {
                    PanelRow(
                        title = "Automática",
                        value = "Se adapta a tu conexión",
                        selected = !fixed,
                        modifier = if (!fixed) Modifier.focusRequester(focus) else Modifier,
                        onClick = { actions.selectQuality(null); back() }
                    )
                }
                qualities.forEach { option ->
                    val selected = fixed && option.selected
                    item {
                        PanelRow(
                            title = option.label,
                            selected = selected,
                            modifier = if (selected) Modifier.focusRequester(focus) else Modifier,
                            onClick = { actions.selectQuality(option); back() }
                        )
                    }
                }
            }

            Page.SCREEN -> {
                backHeader(page, back)
                listOf(
                    Triple(SettingsRepository.RESIZE_FIT, "Ajustar", "Imagen completa, con bordes si hace falta"),
                    Triple(SettingsRepository.RESIZE_ZOOM, "Rellenar", "Llena la pantalla recortando los bordes"),
                    Triple(SettingsRepository.RESIZE_FILL, "Estirar", "Ocupa todo deformando la imagen")
                ).forEach { (mode, title, hint) ->
                    val selected = prefs.resizeMode == mode
                    item {
                        PanelRow(
                            title = title,
                            value = hint,
                            selected = selected,
                            modifier = if (selected) Modifier.focusRequester(focus) else Modifier,
                            onClick = { actions.setResizeMode(mode) }
                        )
                    }
                }
            }

            Page.SPEED -> {
                backHeader(page, back)
                SPEEDS.forEach { value ->
                    val selected = value == speed
                    item {
                        PanelRow(
                            title = speedLabel(value),
                            selected = selected,
                            modifier = if (selected) Modifier.focusRequester(focus) else Modifier,
                            onClick = { actions.setSpeed(value); back() }
                        )
                    }
                }
            }

            Page.SLEEP -> {
                backHeader(page, back)
                item {
                    PanelRow(
                        title = "Desactivado",
                        selected = sleepMinutes == 0,
                        modifier = if (sleepMinutes == 0) Modifier.focusRequester(focus) else Modifier,
                        onClick = { actions.setSleepTimer(0); back() }
                    )
                }
                SLEEP_OPTIONS.forEach { minutes ->
                    val selected = sleepMinutes == minutes
                    item {
                        PanelRow(
                            title = "$minutes minutos",
                            selected = selected,
                            modifier = if (selected) Modifier.focusRequester(focus) else Modifier,
                            onClick = { actions.setSleepTimer(minutes); back() }
                        )
                    }
                }
                if (!isLive) {
                    val selected = sleepMinutes == SLEEP_AT_END
                    item {
                        PanelRow(
                            title = "Al terminar este contenido",
                            selected = selected,
                            modifier = if (selected) Modifier.focusRequester(focus) else Modifier,
                            onClick = { actions.setSleepTimer(SLEEP_AT_END); back() }
                        )
                    }
                }
            }

            Page.INFO -> {
                backHeader(page, back, Modifier.focusRequester(focus))
                item { TechInfoBlock(techInfo) }
            }
        }
    }
}

private fun sleepLabel(minutes: Int, endsAt: Long): String = when {
    minutes == 0 -> "Desactivado"
    minutes == SLEEP_AT_END -> "Al terminar"
    else -> {
        val left = ((endsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) + 1
        "Quedan $left min"
    }
}

private fun LazyListScope.backHeader(page: Page, onBack: () -> Unit, modifier: Modifier = Modifier) {
    item {
        PanelRow(
            title = page.title,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            bold = true,
            modifier = modifier,
            onClick = onBack
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun TechInfoBlock(techInfo: () -> TechInfo) {
    var info by remember { mutableStateOf(techInfo()) }
    LaunchedEffect(Unit) {
        while (true) {
            info = techInfo()
            delay(1_000)
        }
    }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        InfoLine("Resolución", info.resolution ?: "—")
        InfoLine("Códec de video", videoCodecLabel(info.videoCodec) ?: "—")
        InfoLine("Cuadros por segundo", info.frameRate?.let { "%.2f".format(it).trimEnd('0').trimEnd('.', ',') } ?: "—")
        InfoLine("Tasa de video", info.videoBitrate?.let { bitrateLabel(it) } ?: "—")
        InfoLine(
            "Audio",
            listOfNotNull(audioCodecLabel(info.audioCodec), info.audioChannels?.let { channelsLabel(it) })
                .joinToString(" · ").ifEmpty { "—" }
        )
        InfoLine("Búfer", "%.1f s".format(info.bufferedMs / 1000f))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = EntreBrand.TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun PanelSection(text: String) {
    Text(
        text.uppercase(),
        color = EntreBrand.Accent,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun PanelHint(text: String) {
    Text(
        text,
        color = EntreBrand.TextDim,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun PanelRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    chevron: Boolean = false,
    bold: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (focused) Modifier.border(1.5.dp, EntreBrand.Accent, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (focused) Color.White else EntreBrand.TextDim, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold || selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!value.isNullOrEmpty()) {
                Text(
                    value,
                    color = if (selected) EntreBrand.Accent else EntreBrand.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = EntreBrand.Accent, modifier = Modifier.size(22.dp))
        } else if (chevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = EntreBrand.TextDim)
        }
    }
}
