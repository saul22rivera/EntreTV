package com.entretv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Tracks
import com.entretv.app.R
import com.entretv.app.data.settings.PlayerPrefs
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

private enum class Page(@StringRes val title: Int) {
    MAIN(R.string.player_settings_short),
    AUDIO(R.string.player_audio),
    SUBTITLES(R.string.player_subtitles),
    QUALITY(R.string.player_quality),
    SCREEN(R.string.player_screen),
    SPEED(R.string.player_speed),
    SLEEP(R.string.player_sleep_timer),
    INFO(R.string.player_tech_info)
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val SLEEP_OPTIONS = listOf(15, 30, 60)

@StringRes
fun resizeLabelRes(mode: Int): Int = when (mode) {
    SettingsRepository.RESIZE_ZOOM -> R.string.player_resize_zoom
    SettingsRepository.RESIZE_FILL -> R.string.player_resize_fill
    else -> R.string.player_resize_fit
}

@Composable
private fun speedLabel(speed: Float): String =
    if (speed == 1f) stringResource(R.string.player_speed_normal) else "${speed.toString().removeSuffix(".0")}x"

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
                        when {
                            // En TV Compose convierte Atrás en "salir del foco", que aquí está bloqueado:
                            // si no se atiende antes, la tecla se pierde y el panel no se cierra.
                            event.key == Key.Back || event.key == Key.Escape -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    if (page != Page.MAIN) page = Page.MAIN else onDismiss()
                                }
                                true
                            }
                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && page != Page.MAIN -> {
                                page = Page.MAIN
                                true
                            }
                            else -> false
                        }
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
                        onNavigate = { page = it },
                        onClose = onDismiss
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
    onNavigate: (Page) -> Unit,
    onClose: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val context = LocalContext.current
    val audio = tracks.audioOptions(context)
    val subtitles = tracks.subtitleOptions(context)
    val qualities = tracks.qualityOptions(context)
    val back = { onNavigate(Page.MAIN) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        when (page) {
            Page.MAIN -> {
                item {
                    PanelRow(
                        title = stringResource(R.string.player_settings_title),
                        icon = Icons.Default.Close,
                        bold = true,
                        onClick = onClose
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                }
                item {
                    PanelRow(
                        title = stringResource(R.string.player_audio),
                        value = audio.firstOrNull { it.selected }?.label
                            ?: stringResource(if (audio.isEmpty()) R.string.player_no_tracks else R.string.player_audio_auto),
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        chevron = true,
                        modifier = Modifier.focusRequester(focus),
                        onClick = { onNavigate(Page.AUDIO) }
                    )
                }
                item {
                    PanelRow(
                        title = stringResource(R.string.player_subtitles),
                        value = when {
                            subtitles.isEmpty() -> stringResource(R.string.player_subtitles_unavailable)
                            else -> subtitles.firstOrNull { it.selected }?.label ?: stringResource(R.string.player_subtitles_off)
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
                            title = stringResource(R.string.player_quality),
                            value = if (tracks.hasFixedQuality() && current != null) current.label
                            else if (current != null) stringResource(R.string.player_quality_auto_height, current.format.height)
                            else stringResource(R.string.player_quality_auto),
                            icon = Icons.Default.HighQuality,
                            chevron = true,
                            onClick = { onNavigate(Page.QUALITY) }
                        )
                    }
                }
                item {
                    PanelRow(
                        title = stringResource(R.string.player_screen),
                        value = stringResource(resizeLabelRes(prefs.resizeMode)),
                        icon = Icons.Default.AspectRatio,
                        chevron = true,
                        onClick = { onNavigate(Page.SCREEN) }
                    )
                }
                if (!isLive) {
                    item {
                        PanelRow(
                            title = stringResource(R.string.player_speed),
                            value = speedLabel(speed),
                            icon = Icons.Default.Speed,
                            chevron = true,
                            onClick = { onNavigate(Page.SPEED) }
                        )
                    }
                }
                item {
                    PanelRow(
                        title = stringResource(R.string.player_sleep_timer),
                        value = sleepLabel(sleepMinutes, sleepEndsAt),
                        icon = Icons.Default.Bedtime,
                        chevron = true,
                        onClick = { onNavigate(Page.SLEEP) }
                    )
                }
                item {
                    PanelRow(
                        title = stringResource(R.string.player_tech_info),
                        icon = Icons.Default.Info,
                        chevron = true,
                        onClick = { onNavigate(Page.INFO) }
                    )
                }
            }

            Page.AUDIO -> {
                backHeader(page, back, if (audio.isEmpty()) Modifier.focusRequester(focus) else Modifier)
                if (audio.isEmpty()) item { PanelHint(stringResource(R.string.player_audio_none_hint)) }
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
                        title = stringResource(R.string.player_subtitles_off),
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
                if (subtitles.isEmpty()) item { PanelHint(stringResource(R.string.player_subtitles_none_hint)) }
                item { PanelSection(stringResource(R.string.player_subtitle_text_size)) }
                listOf(
                    SettingsRepository.SUBTITLE_SMALL to R.string.player_subtitle_small,
                    SettingsRepository.SUBTITLE_NORMAL to R.string.player_subtitle_normal,
                    SettingsRepository.SUBTITLE_LARGE to R.string.player_subtitle_large
                ).forEach { (size, label) ->
                    item {
                        PanelRow(
                            title = stringResource(label),
                            selected = prefs.subtitleSize == size,
                            onClick = { actions.setSubtitleSize(size) }
                        )
                    }
                }
                item { PanelHint(stringResource(R.string.player_subtitles_hint)) }
            }

            Page.QUALITY -> {
                backHeader(page, back)
                val fixed = tracks.hasFixedQuality()
                item {
                    PanelRow(
                        title = stringResource(R.string.player_quality_auto),
                        value = stringResource(R.string.player_quality_auto_hint),
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
                    SettingsRepository.RESIZE_FIT to R.string.player_resize_fit_hint,
                    SettingsRepository.RESIZE_ZOOM to R.string.player_resize_zoom_hint,
                    SettingsRepository.RESIZE_FILL to R.string.player_resize_fill_hint
                ).forEach { (mode, hint) ->
                    val selected = prefs.resizeMode == mode
                    item {
                        PanelRow(
                            title = stringResource(resizeLabelRes(mode)),
                            value = stringResource(hint),
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
                        title = stringResource(R.string.player_sleep_off),
                        selected = sleepMinutes == 0,
                        modifier = if (sleepMinutes == 0) Modifier.focusRequester(focus) else Modifier,
                        onClick = { actions.setSleepTimer(0); back() }
                    )
                }
                SLEEP_OPTIONS.forEach { minutes ->
                    val selected = sleepMinutes == minutes
                    item {
                        PanelRow(
                            title = pluralStringResource(R.plurals.player_sleep_minutes, minutes, minutes),
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
                            title = stringResource(R.string.player_sleep_at_end_option),
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

@Composable
private fun sleepLabel(minutes: Int, endsAt: Long): String = when {
    minutes == 0 -> stringResource(R.string.player_sleep_off)
    minutes == SLEEP_AT_END -> stringResource(R.string.player_sleep_at_end)
    else -> {
        val left = (((endsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) + 1).toInt()
        pluralStringResource(R.plurals.player_minutes_left, left, left)
    }
}

private fun LazyListScope.backHeader(page: Page, onBack: () -> Unit, modifier: Modifier = Modifier) {
    item {
        PanelRow(
            title = stringResource(page.title),
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
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        InfoLine(stringResource(R.string.player_info_resolution), info.resolution ?: "—")
        InfoLine(stringResource(R.string.player_info_video_codec), videoCodecLabel(info.videoCodec) ?: "—")
        InfoLine(
            stringResource(R.string.player_info_frame_rate),
            info.frameRate?.let { "%.2f".format(it).trimEnd('0').trimEnd('.', ',') } ?: "—"
        )
        InfoLine(stringResource(R.string.player_info_video_bitrate), info.videoBitrate?.let { bitrateLabel(it) } ?: "—")
        InfoLine(
            stringResource(R.string.player_audio),
            listOfNotNull(audioCodecLabel(info.audioCodec), info.audioChannels?.let { channelsLabel(context, it) })
                .joinToString(" · ").ifEmpty { "—" }
        )
        InfoLine(stringResource(R.string.player_info_buffer), "%.1f s".format(info.bufferedMs / 1000f))
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
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.player_selected), tint = EntreBrand.Accent, modifier = Modifier.size(22.dp))
        } else if (chevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = EntreBrand.TextDim)
        }
    }
}
