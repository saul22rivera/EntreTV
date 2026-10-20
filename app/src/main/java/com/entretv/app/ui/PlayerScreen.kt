package com.entretv.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.player.LiveChannelList
import com.entretv.app.ui.player.PlayerSettingsPanel
import com.entretv.app.ui.theme.EntreBrand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 4_000L
private const val NEXT_EPISODE_WINDOW_MS = 40_000L
private const val DOUBLE_TAP_SEEK_MS = 10_000L

/** Aviso flotante de gestos y saltos: "+10 s", brillo, volumen. */
private data class Feedback(val icon: ImageVector, val text: String, val level: Float? = null)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val manager = viewModel.playerManager
    val profile = rememberDeviceProfile()
    val playable by viewModel.playable.collectAsState()
    val epg by viewModel.epg.collectAsState()
    val buffering by manager.isBuffering.collectAsState()
    val isPlaying by manager.isPlayingFlow.collectAsState()
    val duration by manager.durationMs.collectAsState()
    val reconnecting by manager.reconnecting.collectAsState()
    val playError by manager.error.collectAsState()
    val tracks by manager.tracks.collectAsState()
    val speed by manager.speed.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val prefs by viewModel.playerPrefs.collectAsState()
    val sleepMinutes by viewModel.sleepMinutes.collectAsState()
    val sleepEndsAt by viewModel.sleepEndsAt.collectAsState()
    val nextEpisode by viewModel.nextEpisode.collectAsState()
    val groupChannels by viewModel.groupChannels.collectAsState()

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var channelsOpen by rememberSaveable { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }

    val isLive = playable?.kind == "LIVE"
    val overlayOpen = panelOpen || channelsOpen
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    fun showControls() {
        controlsVisible = true
        interaction++
    }

    fun togglePlay() {
        val p = manager.player
        if (p.isPlaying) p.pause() else p.play()
        showControls()
    }

    fun seekBy(deltaMs: Long) {
        val p = manager.player
        val target = (p.currentPosition + deltaMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        viewModel.seekTo(target)
        position = target
        feedback = Feedback(
            if (deltaMs < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
            "${if (deltaMs < 0) "−" else "+"}${kotlin.math.abs(deltaMs) / 1000} s · ${PlayerViewModel.formatMs(target)}"
        )
    }

    fun openPanel() {
        channelsOpen = false
        controlsVisible = false
        panelOpen = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> manager.player.pause()
                Lifecycle.Event.ON_START -> manager.player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Se ocultan solos mientras reproduce y nadie toca nada.
    LaunchedEffect(controlsVisible, interaction, overlayOpen, isPlaying, dragging) {
        if (controlsVisible && isPlaying && !overlayOpen && !dragging) {
            delay(AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(playable) {
        while (true) {
            if (!dragging) position = manager.player.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(900)
            feedback = null
        }
    }

    val remaining = duration - position
    val showNextEpisode = nextEpisode != null && duration > 0 &&
        remaining in 1..NEXT_EPISODE_WINDOW_MS && !overlayOpen

    // En TV el foco va a Play al mostrar controles y vuelve a la raíz al ocultarlos.
    LaunchedEffect(controlsVisible, overlayOpen, showNextEpisode) {
        if (overlayOpen) return@LaunchedEffect
        delay(60)
        runCatching {
            when {
                controlsVisible -> playFocus.requestFocus()
                showNextEpisode && profile.isTv -> nextFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    BackHandler(enabled = profile.isTv && controlsVisible && !overlayOpen) { controlsVisible = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interaction++
                when (event.key) {
                    Key.Menu -> {
                        if (panelOpen) panelOpen = false else openPanel()
                        true
                    }
                    Key.MediaPlayPause -> { togglePlay(); true }
                    Key.MediaPlay -> { manager.player.play(); true }
                    Key.MediaPause -> { manager.player.pause(); showControls(); true }
                    Key.ChannelUp -> { viewModel.zap(1); true }
                    Key.ChannelDown -> { viewModel.zap(-1); true }
                    Key.MediaFastForward -> { if (!isLive) seekBy(30_000); true }
                    Key.MediaRewind -> { if (!isLive) seekBy(-10_000); true }
                    else -> {
                        if (overlayOpen || controlsVisible) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (showNextEpisode) false else { showControls(); true }
                            }
                            Key.DirectionLeft -> {
                                if (isLive) { if (groupChannels.size > 1) channelsOpen = true } else seekBy(-10_000)
                                true
                            }
                            Key.DirectionRight -> {
                                if (isLive) showControls() else seekBy(30_000)
                                true
                            }
                            Key.DirectionUp -> {
                                if (isLive) viewModel.zap(1) else showControls()
                                true
                            }
                            Key.DirectionDown -> {
                                if (isLive) viewModel.zap(-1) else showControls()
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    player = manager.player
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            0x99000000.toInt(),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    subtitleView?.setApplyEmbeddedFontSizes(false)
                }
            },
            update = { view ->
                view.keepScreenOn = isPlaying || buffering || reconnecting
                view.resizeMode = when (prefs.resizeMode) {
                    SettingsRepository.RESIZE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    SettingsRepository.RESIZE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                val scale = when (prefs.subtitleSize) {
                    SettingsRepository.SUBTITLE_SMALL -> 0.8f
                    SettingsRepository.SUBTITLE_LARGE -> 1.4f
                    else -> 1f
                }
                view.subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
            },
            modifier = Modifier.fillMaxSize()
        )

        GestureLayer(
            enabled = !profile.isTv,
            seekEnabled = !isLive,
            onTap = { if (controlsVisible) controlsVisible = false else showControls() },
            onDoubleTapSeek = { forward -> seekBy(if (forward) DOUBLE_TAP_SEEK_MS else -DOUBLE_TAP_SEEK_MS) },
            onFeedback = { feedback = it }
        )

        when {
            playError != null -> Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(playError.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { manager.retry() }) { Text("Reintentar") }
                }
            }
            reconnecting -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Reconectando…", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            buffering && !controlsVisible -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !overlayOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                TopBar(
                    title = playable?.title ?: "Cargando…",
                    subtitle = playable?.subtitle.orEmpty(),
                    isLive = isLive,
                    isFavorite = isFavorite,
                    showChannels = isLive && groupChannels.size > 1,
                    onBack = onBack,
                    onChannels = { controlsVisible = false; channelsOpen = true },
                    onFavorite = { viewModel.toggleFavorite(); interaction++ },
                    onSettings = ::openPanel,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (profile.isTv) 28.dp else 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playable?.kind != "MOVIE") {
                        ControlIcon(Icons.AutoMirrored.Filled.NavigateBefore, if (isLive) "Canal anterior" else "Episodio anterior", 40.dp) {
                            viewModel.zap(-1); interaction++
                        }
                    }
                    if (!isLive) {
                        ControlIcon(Icons.Default.Replay10, "Atrasar 10 segundos", 40.dp) { seekBy(-10_000); interaction++ }
                    }
                    if (buffering) {
                        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                        }
                    } else {
                        ControlIcon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pausar" else "Reproducir",
                            size = 60.dp,
                            emphasized = true,
                            modifier = Modifier.focusRequester(playFocus),
                            onClick = ::togglePlay
                        )
                    }
                    if (!isLive) {
                        ControlIcon(Icons.Default.Forward30, "Adelantar 30 segundos", 40.dp) { seekBy(30_000); interaction++ }
                    }
                    if (playable?.kind != "MOVIE") {
                        ControlIcon(Icons.AutoMirrored.Filled.NavigateNext, if (isLive) "Canal siguiente" else "Episodio siguiente", 40.dp) {
                            viewModel.zap(1); interaction++
                        }
                    }
                }

                BottomBar(
                    isLive = isLive,
                    position = position,
                    duration = duration,
                    speed = speed,
                    epg = epg,
                    onSeekChange = { dragging = true; position = it; interaction++ },
                    onSeekDone = { dragging = false; viewModel.seekTo(position) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        AnimatedVisibility(
            visible = showNextEpisode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = if (controlsVisible) 110.dp else 32.dp)
        ) {
            nextEpisode?.let { next ->
                Column(horizontalAlignment = Alignment.End) {
                    EntreButton(
                        text = "Siguiente episodio",
                        icon = Icons.Default.SkipNext,
                        primary = true,
                        onClick = viewModel::playNextEpisode,
                        modifier = Modifier.focusRequester(nextFocus)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "T${next.season}:E${next.episodeNum} · en ${(remaining / 1000).coerceAtLeast(0)} s",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        val currentFeedback = feedback
        AnimatedVisibility(
            visible = currentFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            currentFeedback?.let { FeedbackBubble(it) }
        }

        notice?.let { msg ->
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
            ) {
                Text(
                    msg,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        LiveChannelList(
            visible = channelsOpen,
            channels = groupChannels,
            currentId = playable?.contentKey,
            nowPlaying = viewModel::nowPlaying,
            onSelect = { channel ->
                viewModel.playChannel(channel)
                channelsOpen = false
            },
            onDismiss = { channelsOpen = false }
        )

        PlayerSettingsPanel(
            visible = panelOpen,
            tracks = tracks,
            prefs = prefs,
            speed = speed,
            isLive = isLive,
            sleepMinutes = sleepMinutes,
            sleepEndsAt = sleepEndsAt,
            techInfo = manager::techInfo,
            actions = viewModel,
            onDismiss = { panelOpen = false }
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    isLive: Boolean,
    isFavorite: Boolean,
    showChannels: Boolean,
    onBack: () -> Unit,
    onChannels: () -> Unit,
    onFavorite: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlIcon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", 26.dp, onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLive) {
                    Surface(color = Color(0xFFE53935), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "EN VIVO",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showChannels) ControlIcon(Icons.AutoMirrored.Filled.List, "Canales", 26.dp, onClick = onChannels)
        ControlIcon(
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (isFavorite) "Quitar de Mi lista" else "Agregar a Mi lista",
            26.dp,
            tint = if (isFavorite) EntreBrand.Accent else Color.White,
            onClick = onFavorite
        )
        ControlIcon(Icons.Default.Settings, "Ajustes de reproducción", 26.dp, onClick = onSettings)
    }
}

@Composable
private fun BottomBar(
    isLive: Boolean,
    position: Long,
    duration: Long,
    speed: Float,
    epg: List<com.entretv.app.data.local.EpgEntity>,
    onSeekChange: (Long) -> Unit,
    onSeekDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (!isLive && duration > 0) {
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat()),
                valueRange = 0f..duration.toFloat(),
                onValueChange = { onSeekChange(it.toLong()) },
                onValueChangeFinished = onSeekDone,
                colors = SliderDefaults.colors(
                    thumbColor = EntreBrand.Accent,
                    activeTrackColor = EntreBrand.Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(PlayerViewModel.formatMs(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                if (speed != 1f) {
                    Text(
                        "${speed.toString().removeSuffix(".0")}x",
                        color = EntreBrand.Accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    "−" + PlayerViewModel.formatMs((duration - position).coerceAtLeast(0L)),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        } else if (isLive && epg.isNotEmpty()) {
            val now = System.currentTimeMillis() / 1000
            val current = epg.first()
            if (current.start <= now && current.end > now) {
                Text(
                    "Ahora · ${current.title}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { ((now - current.start).toFloat() / (current.end - current.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = EntreBrand.Accent,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(8.dp))
            }
            epg.filter { it.start > now }.take(2).forEach { program ->
                Row(Modifier.padding(vertical = 1.dp)) {
                    Text(timeFormat(program.start), color = EntreBrand.Accent, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        program.title,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Botón redondo del reproductor con foco visible para el control remoto. */
@Composable
private fun ControlIcon(
    icon: ImageVector,
    description: String,
    size: Dp,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "controlScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.28f)
                    emphasized -> Color.White.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(2.dp, EntreBrand.Accent, CircleShape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(if (emphasized) 12.dp else 8.dp)
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun FeedbackBubble(feedback: Feedback) {
    Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(18.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Icon(feedback.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(6.dp))
            Text(feedback.text, color = Color.White, style = MaterialTheme.typography.titleSmall)
            feedback.level?.let { level ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { level },
                    color = EntreBrand.Accent,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.width(120.dp).height(4.dp).clip(CircleShape)
                )
            }
        }
    }
}

/**
 * Gestos del celular: toque muestra/oculta, doble toque salta ±10 s,
 * deslizar vertical a la izquierda cambia el brillo y a la derecha el volumen.
 */
@Composable
private fun GestureLayer(
    enabled: Boolean,
    seekEnabled: Boolean,
    onTap: () -> Unit,
    onDoubleTapSeek: (forward: Boolean) -> Unit,
    onFeedback: (Feedback) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    if (!enabled) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onTap() } }
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(seekEnabled) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = if (seekEnabled) {
                        { offset -> onDoubleTapSeek(offset.x > size.width / 2f) }
                    } else null
                )
            }
            .pointerInput(Unit) {
                var adjustBrightness = false
                var level = 0f
                val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        adjustBrightness = offset.x < size.width / 2f
                        level = if (adjustBrightness) currentBrightness(activity)
                        else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        level = (level - dragAmount / (size.height * 0.8f)).coerceIn(0f, 1f)
                        if (adjustBrightness) {
                            activity?.window?.let { window ->
                                window.attributes = window.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) }
                            }
                            onFeedback(Feedback(Icons.Default.BrightnessMedium, "Brillo ${(level * 100).toInt()}%", level))
                        } else {
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * maxVolume).toInt(), 0)
                            onFeedback(Feedback(Icons.AutoMirrored.Filled.VolumeUp, "Volumen ${(level * 100).toInt()}%", level))
                        }
                    }
                )
            }
    )
}

private fun currentBrightness(activity: Activity?): Float {
    val override = activity?.window?.attributes?.screenBrightness ?: -1f
    if (override >= 0f) return override
    val system = runCatching {
        Settings.System.getInt(activity?.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (system / 255f).coerceIn(0f, 1f)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun timeFormat(epochSeconds: Long): String = formatter.format(Date(epochSeconds * 1000))
