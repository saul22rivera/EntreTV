package com.entretv.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.media3.ui.PlayerView
import com.entretv.app.R
import com.entretv.app.data.local.EpgEntity
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.player.PipController
import com.entretv.app.ui.player.ChannelBrowser
import com.entretv.app.ui.player.ChannelBrowserState
import com.entretv.app.ui.player.FrameRateMatchEffect
import com.entretv.app.ui.player.LiveChannelList
import com.entretv.app.ui.player.PlayerSettingsPanel
import com.entretv.app.ui.player.applyUserStyle
import com.entretv.app.ui.player.resizeLabelRes
import com.entretv.app.ui.theme.EntreBrand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 4_000L
/** Créditos: la tarjeta "Siguiente episodio en N" cuenta lo que falta y al llegar a 0 pasa sola. */
private const val NEXT_EPISODE_WINDOW_MS = 30_000L
private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val SCRUB_COMMIT_MS = 1_200L

/** Aviso flotante de gestos y saltos: "+10 s", brillo, volumen, formato de imagen. */
private data class Feedback(val icon: ImageVector, val text: String, val level: Float? = null, val durationMs: Long = 900)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val manager = viewModel.playerManager
    val profile = rememberDeviceProfile()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val playable by viewModel.playable.collectAsState()
    val epg by viewModel.epg.collectAsState()
    val buffering by manager.isBuffering.collectAsState()
    val isPlaying by manager.isPlayingFlow.collectAsState()
    val duration by manager.durationMs.collectAsState()
    val reconnecting by manager.reconnecting.collectAsState()
    val reconnectAttempt by manager.reconnectAttempt.collectAsState()
    val offline by manager.offline.collectAsState()
    val playError by manager.error.collectAsState()
    val tracks by manager.tracks.collectAsState()
    val speed by manager.speed.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val prefs by viewModel.playerPrefs.collectAsState()
    val showClock by viewModel.showClock.collectAsState()
    val sleepMinutes by viewModel.sleepMinutes.collectAsState()
    val sleepEndsAt by viewModel.sleepEndsAt.collectAsState()
    val nextEpisode by viewModel.nextEpisode.collectAsState()
    val autoNextCanceled by viewModel.autoNextCanceled.collectAsState()
    val introEnd by viewModel.introEndMs.collectAsState()
    val introDone by viewModel.introDone.collectAsState()
    val liveCategories by viewModel.liveCategories.collectAsState()
    val browseCategory by viewModel.browseCategory.collectAsState()
    val browseQuery by viewModel.browseQuery.collectAsState()
    val browseChannels by viewModel.browseChannels.collectAsState()
    val inPip by PipController.active.collectAsState()
    val currentKey by manager.currentKey.collectAsState()

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var channelsOpen by rememberSaveable { mutableStateOf(false) }
    var liveTab by rememberSaveable { mutableIntStateOf(0) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }

    val isLive = playable?.kind == "LIVE"
    val portrait = !profile.isTv && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // Celular vertical en vivo: video arriba y guía/canales debajo, sin taparlo.
    val portraitLive = portrait && isLive && !inPip
    val pipSupported = remember(context) { !profile.isTv && PipController.isSupported(context) }
    val overlayOpen = panelOpen || (channelsOpen && !portraitLive)
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val skipIntroFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }

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
            context.getString(
                if (deltaMs < 0) R.string.player_feedback_rewind else R.string.player_feedback_forward,
                (kotlin.math.abs(deltaMs) / 1000).toInt(),
                PlayerViewModel.formatMs(target)
            )
        )
    }

    fun openPanel() {
        channelsOpen = false
        controlsVisible = false
        panelOpen = true
    }

    fun openChannels() {
        viewModel.resetBrowse()
        panelOpen = false
        controlsVisible = false
        channelsOpen = true
    }

    fun cycleAspect() {
        val next = when (prefs.resizeMode) {
            SettingsRepository.RESIZE_FIT -> SettingsRepository.RESIZE_ZOOM
            SettingsRepository.RESIZE_ZOOM -> SettingsRepository.RESIZE_FILL
            else -> SettingsRepository.RESIZE_FIT
        }
        viewModel.setResizeMode(next)
        val detail = when (next) {
            SettingsRepository.RESIZE_ZOOM -> R.string.player_resize_detail_zoom
            SettingsRepository.RESIZE_FILL -> R.string.player_resize_detail_fill
            else -> R.string.player_resize_detail_fit
        }
        feedback = Feedback(
            Icons.Default.AspectRatio,
            "${context.getString(resizeLabelRes(next))} · ${context.getString(detail)}",
            durationMs = 1_800
        )
        interaction++
    }

    fun enterPip() {
        val ok = activity?.let { PipController.enter(it) } == true
        if (!ok) feedback = Feedback(Icons.Default.PictureInPictureAlt, context.getString(R.string.player_pip_unsupported), durationMs = 1_800)
    }

    // En vivo no: zapear entre canales de 50 y 60 Hz dejaría la TV en negro en cada cambio.
    FrameRateMatchEffect(
        player = manager.player,
        activity = activity,
        enabled = profile.isTv && playable != null && !isLive &&
            prefs.frameRateMode == SettingsRepository.FRAME_RATE_AUTO,
        contentKey = currentKey
    )

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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            PipController.videoBounds = null
        }
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
        feedback?.let {
            delay(it.durationMs)
            feedback = null
        }
    }

    val remaining = duration - position
    val showNextEpisode = nextEpisode != null && duration > 0 && !autoNextCanceled &&
        remaining in 1..NEXT_EPISODE_WINDOW_MS && !overlayOpen && !inPip
    val introLimit = introEnd?.let { it - PlayerViewModel.INTRO_END_MARGIN_MS } ?: PlayerViewModel.INTRO_WINDOW_MS
    val showSkipIntro = prefs.skipIntroButton && playable is Playable.Episode && !introDone &&
        position in PlayerViewModel.INTRO_SHOW_FROM_MS until introLimit &&
        !showNextEpisode && !overlayOpen && !inPip && playError == null

    // En TV el foco va a Play al mostrar controles y vuelve a la raíz al ocultarlos.
    LaunchedEffect(controlsVisible, overlayOpen, showNextEpisode, showSkipIntro) {
        if (overlayOpen) return@LaunchedEffect
        delay(60)
        runCatching {
            when {
                controlsVisible -> playFocus.requestFocus()
                showNextEpisode && profile.isTv -> nextFocus.requestFocus()
                showSkipIntro && profile.isTv -> skipIntroFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    BackHandler(enabled = profile.isTv && controlsVisible && !overlayOpen) { controlsVisible = false }

    val browserState = ChannelBrowserState(
        categories = liveCategories,
        category = browseCategory,
        query = browseQuery,
        channels = browseChannels,
        currentId = playable?.contentKey,
        nowPlaying = viewModel::nowPlaying,
        onCategory = viewModel::browse,
        onQuery = viewModel::searchChannels,
        onSelect = { channel ->
            viewModel.playChannel(channel)
            if (!portraitLive) channelsOpen = false
        }
    )

    val videoStack: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier.onGloballyPositioned { coords ->
                val r = coords.boundsInWindow()
                PipController.videoBounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = manager.player
                    }
                },
                update = { view ->
                    view.keepScreenOn = isPlaying || buffering || reconnecting
                    view.resizeMode = when (prefs.resizeMode) {
                        SettingsRepository.RESIZE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        SettingsRepository.RESIZE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    view.subtitleView?.applyUserStyle(prefs)
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!inPip) {
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
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Text(playError?.asString().orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { manager.retry() }) { Text(stringResource(R.string.player_retry)) }
                        }
                    }
                    buffering && !controlsVisible && !reconnecting -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (reconnecting && playError == null) {
                    ReconnectPill(
                        offline = offline,
                        attempt = reconnectAttempt,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (controlsVisible) 64.dp else 12.dp)
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible && !overlayOpen,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // En vertical los botones de acción bajan (pastillas), más cerca del pulgar.
                        TopBar(
                            title = playable?.title ?: stringResource(R.string.player_loading),
                            subtitle = when (val p = playable) {
                                is Playable.Episode -> stringResource(
                                    R.string.player_episode_subtitle,
                                    p.episode.season, p.episode.episodeNum, p.episode.title
                                )
                                else -> p?.subtitle.orEmpty()
                            },
                            isLive = isLive,
                            isFavorite = isFavorite,
                            showActions = !portrait,
                            showChannels = isLive && !portraitLive,
                            onBack = onBack,
                            onChannels = ::openChannels,
                            onFavorite = { viewModel.toggleFavorite(); interaction++ },
                            onAspect = if (!profile.isTv) ::cycleAspect else null,
                            onPip = if (pipSupported) ::enterPip else null,
                            onSettings = ::openPanel,
                            showClock = showClock,
                            clockWithDate = !portrait,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        val small = portraitLive
                        val hasSeekBar = profile.isTv && !isLive && duration > 0
                        val downToSeek = Modifier.focusProperties {
                            if (hasSeekBar) down = seekFocus
                        }
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(if (profile.isTv) 28.dp else if (small) 14.dp else 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (playable?.kind != "MOVIE") {
                                ControlIcon(
                                    Icons.AutoMirrored.Filled.NavigateBefore,
                                    stringResource(if (isLive) R.string.player_prev_channel else R.string.player_prev_episode),
                                    if (small) 32.dp else 40.dp
                                ) { viewModel.zap(-1); interaction++ }
                            }
                            if (!isLive) {
                                ControlIcon(Icons.Default.Replay10, stringResource(R.string.player_rewind_10), 40.dp, modifier = downToSeek) { seekBy(-10_000); interaction++ }
                            }
                            if (buffering) {
                                Box(Modifier.size(if (small) 64.dp else 84.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(if (small) 40.dp else 56.dp)
                                    )
                                }
                            } else {
                                ControlIcon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                                    size = if (small) 44.dp else 60.dp,
                                    emphasized = true,
                                    modifier = Modifier.focusRequester(playFocus).then(downToSeek),
                                    onClick = ::togglePlay
                                )
                            }
                            if (!isLive) {
                                ControlIcon(Icons.Default.Forward30, stringResource(R.string.player_forward_30), 40.dp, modifier = downToSeek) { seekBy(30_000); interaction++ }
                            }
                            if (playable?.kind != "MOVIE") {
                                ControlIcon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    stringResource(if (isLive) R.string.player_next_channel else R.string.player_next_episode),
                                    if (small) 32.dp else 40.dp
                                ) { viewModel.zap(1); interaction++ }
                            }
                        }

                        Column(Modifier.align(Alignment.BottomCenter)) {
                            if (portrait && !portraitLive) {
                                ActionPills(
                                    isFavorite = isFavorite,
                                    resizeMode = prefs.resizeMode,
                                    pipSupported = pipSupported,
                                    onFavorite = { viewModel.toggleFavorite(); interaction++ },
                                    onAspect = ::cycleAspect,
                                    onPip = ::enterPip,
                                    onSettings = ::openPanel,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                )
                            }
                            if (!portraitLive) {
                                BottomBar(
                                    isLive = isLive,
                                    position = position,
                                    duration = duration,
                                    speed = speed,
                                    epg = epg,
                                    tv = profile.isTv,
                                    seekFocus = seekFocus,
                                    onSeekChange = {
                                        dragging = true; position = it; interaction++
                                    },
                                    onSeekDone = {
                                        dragging = false; viewModel.seekTo(position)
                                    },
                                    modifier = if (portrait) Modifier.navigationBarsPadding() else Modifier
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showNextEpisode,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = if (controlsVisible) (if (portrait) 170.dp else 110.dp) else 32.dp)
                ) {
                    nextEpisode?.let { next ->
                        NextEpisodeCard(
                            next = next,
                            fallbackImage = (playable as? Playable.Episode)?.seriesCover.orEmpty(),
                            secondsLeft = ((remaining + 999) / 1000).coerceAtLeast(0).toInt(),
                            compact = portrait,
                            playFocus = nextFocus,
                            onPlayNow = viewModel::playNextEpisode,
                            onCancel = viewModel::cancelAutoNext
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showSkipIntro,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = if (controlsVisible) (if (portrait) 170.dp else 110.dp) else 32.dp)
                ) {
                    // Fondo oscuro debajo: el botón translúcido se pierde en escenas claras.
                    Box(Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))) {
                        EntreButton(
                            text = stringResource(R.string.player_skip_intro),
                            icon = Icons.Default.SkipNext,
                            onClick = viewModel::skipIntro,
                            modifier = Modifier.focusRequester(skipIntroFocus)
                        )
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
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            // Respaldo en TV si Atrás llega a la raíz (el foco quedó fuera del panel o de los
            // controles): sin esto Compose lo gasta moviendo el foco y no pasa nada.
            .onKeyEvent { event ->
                if (!profile.isTv || (event.key != Key.Back && event.key != Key.Escape)) return@onKeyEvent false
                val handled = panelOpen || channelsOpen || controlsVisible
                if (handled && event.type == KeyEventType.KeyDown) {
                    when {
                        panelOpen -> panelOpen = false
                        channelsOpen -> channelsOpen = false
                        else -> controlsVisible = false
                    }
                }
                handled
            }
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
                        if (overlayOpen || controlsVisible || portraitLive) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (showNextEpisode || showSkipIntro) false else { showControls(); true }
                            }
                            // Con la tarjeta del siguiente episodio, izquierda y derecha van entre sus botones.
                            Key.DirectionLeft -> {
                                if (showNextEpisode) return@onPreviewKeyEvent false
                                if (isLive) openChannels() else seekBy(-10_000)
                                true
                            }
                            Key.DirectionRight -> {
                                if (showNextEpisode) return@onPreviewKeyEvent false
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
        if (portraitLive) {
            Column(Modifier.fillMaxSize()) {
                videoStack(
                    Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(EntreBrand.Ink)
                        .navigationBarsPadding()
                ) {
                    Text(
                        playable?.title.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    )
                    ActionPills(
                        isFavorite = isFavorite,
                        resizeMode = prefs.resizeMode,
                        pipSupported = pipSupported,
                        onFavorite = viewModel::toggleFavorite,
                        onAspect = ::cycleAspect,
                        onPip = ::enterPip,
                        onSettings = ::openPanel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LiveTabs(selected = liveTab, onSelect = { liveTab = it })
                    when (liveTab) {
                        0 -> EpgGuide(epg, Modifier.weight(1f))
                        else -> ChannelBrowser(browserState, twoColumns = false, focusCurrent = false, modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            videoStack(Modifier.fillMaxSize())
        }

        if (!inPip) {
            notice?.asString()?.let { msg ->
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
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

            if (!portraitLive) {
                LiveChannelList(
                    visible = channelsOpen,
                    state = browserState,
                    onDismiss = { channelsOpen = false }
                )
            }

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
}

/** Aviso chico arriba del video: no tapa la imagen mientras se recupera solo. */
/** "Siguiente episodio en N" con miniatura, Reproducir ahora y Cancelar. */
@Composable
private fun NextEpisodeCard(
    next: EpisodeEntity,
    fallbackImage: String,
    secondsLeft: Int,
    compact: Boolean,
    playFocus: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(color = Color.Black.copy(alpha = 0.82f), shape = RoundedCornerShape(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            if (!compact) {
                FallbackImage(
                    urls = listOf(next.imageUrl, fallbackImage).filter { it.isNotBlank() }.distinct(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.width(if (compact) 240.dp else 300.dp)) {
                Text(
                    stringResource(R.string.player_up_next_in, secondsLeft),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.player_episode_subtitle, next.season, next.episodeNum, next.title),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EntreButton(
                        text = stringResource(R.string.player_play_now),
                        icon = Icons.Default.PlayArrow,
                        primary = true,
                        compact = true,
                        onClick = onPlayNow,
                        modifier = Modifier.focusRequester(playFocus)
                    )
                    EntreButton(
                        text = stringResource(R.string.player_cancel),
                        icon = Icons.Default.Close,
                        compact = true,
                        onClick = onCancel
                    )
                }
            }
        }
    }
}

@Composable
private fun ReconnectPill(offline: Boolean, attempt: Int, modifier: Modifier = Modifier) {
    Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(50), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            if (offline) {
                Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                CircularProgressIndicator(color = EntreBrand.Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    offline -> stringResource(R.string.player_offline)
                    attempt >= 4 -> stringResource(R.string.player_reconnecting_attempt, attempt)
                    else -> stringResource(R.string.player_reconnecting)
                },
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Pastillas translúcidas del celular: Mi lista, formato de imagen, ventana flotante y ajustes. */
@Composable
private fun ActionPills(
    isFavorite: Boolean,
    resizeMode: Int,
    pipSupported: Boolean,
    onFavorite: () -> Unit,
    onAspect: () -> Unit,
    onPip: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Cada pastilla toma a lo sumo una parte igual del ancho: en un celular angosto entran las cuatro.
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        val share = Modifier.weight(1f, fill = false)
        ActionPill(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = stringResource(if (isFavorite) R.string.player_in_my_list else R.string.player_my_list),
            tint = if (isFavorite) EntreBrand.Accent else Color.White,
            onClick = onFavorite,
            modifier = share
        )
        ActionPill(Icons.Default.AspectRatio, stringResource(resizeLabelRes(resizeMode)), onClick = onAspect, modifier = share)
        if (pipSupported) {
            ActionPill(Icons.Default.PictureInPictureAlt, stringResource(R.string.player_pip_short), onClick = onPip, modifier = share)
        }
        ActionPill(Icons.Default.Settings, stringResource(R.string.player_settings_short), onClick = onSettings, modifier = share)
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = if (focused) 0.8f else 0.55f))
            .border(1.dp, Color.White.copy(alpha = if (focused) 0.9f else 0.2f), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LiveTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        listOf(stringResource(R.string.player_tab_guide), stringResource(R.string.player_channels)).forEachIndexed { index, label ->
            val active = index == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = if (active) Color.White else EntreBrand.TextDim,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .width(48.dp)
                        .clip(CircleShape)
                        .background(if (active) EntreBrand.Accent else Color.Transparent)
                )
            }
        }
    }
}

/** Guía del canal: lo que pasa ahora (con avance), lo que sigue y más tarde. */
@Composable
private fun EpgGuide(epg: List<EpgEntity>, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis() / 1000
    val current = epg.firstOrNull { it.start <= now && it.end > now }
    val upcoming = epg.filter { it.start > now }
    if (current == null && upcoming.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                stringResource(R.string.player_epg_empty),
                color = EntreBrand.TextDim,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        current?.let { program ->
            item(key = "now") {
                Surface(color = EntreBrand.Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color(0xFFE53935), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    stringResource(R.string.player_epg_now_badge),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${timeFormat(program.start)} – ${timeFormat(program.end)}",
                                color = EntreBrand.TextDim,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.weight(1f))
                            val minutesLeft = ((program.end - now) / 60).coerceAtLeast(1).toInt()
                            Text(
                                pluralStringResource(R.plurals.player_minutes_left, minutesLeft, minutesLeft),
                                color = EntreBrand.TextDim,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(program.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { ((now - program.start).toFloat() / (program.end - program.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                            color = EntreBrand.Accent,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        )
                        if (program.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                program.description,
                                color = EntreBrand.Text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        upcoming.firstOrNull()?.let { next ->
            item(key = "next-title") { GuideHeader(stringResource(R.string.player_epg_up_next)) }
            item(key = "next") { GuideRow(next, showDescription = true) }
        }
        if (upcoming.size > 1) {
            item(key = "later-title") { GuideHeader(stringResource(R.string.player_epg_later)) }
            items(upcoming.drop(1), key = { "${it.start}-${it.title}" }) { GuideRow(it, showDescription = false) }
        }
    }
}

@Composable
private fun GuideHeader(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun GuideRow(program: EpgEntity, showDescription: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            timeFormat(program.start),
            color = EntreBrand.Accent,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(56.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(program.title, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (showDescription && program.description.isNotBlank()) {
                Text(
                    program.description,
                    color = EntreBrand.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    isLive: Boolean,
    isFavorite: Boolean,
    showActions: Boolean,
    showChannels: Boolean,
    onBack: () -> Unit,
    onChannels: () -> Unit,
    onFavorite: () -> Unit,
    onAspect: (() -> Unit)?,
    onPip: (() -> Unit)?,
    onSettings: () -> Unit,
    showClock: Boolean = false,
    clockWithDate: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.player_back), 26.dp, onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLive) {
                    Surface(color = Color(0xFFE53935), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            stringResource(R.string.player_live_badge),
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
        if (showClock) SmallClock(Modifier.padding(horizontal = 8.dp), showDate = clockWithDate)
        if (showChannels) ControlIcon(Icons.AutoMirrored.Filled.List, stringResource(R.string.player_channels), 26.dp, onClick = onChannels)
        if (showActions) {
            onAspect?.let { ControlIcon(Icons.Default.AspectRatio, stringResource(R.string.player_aspect), 26.dp, onClick = it) }
            onPip?.let { ControlIcon(Icons.Default.PictureInPictureAlt, stringResource(R.string.player_pip), 26.dp, onClick = it) }
            ControlIcon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                stringResource(if (isFavorite) R.string.player_remove_from_list else R.string.player_add_to_list),
                26.dp,
                tint = if (isFavorite) EntreBrand.Accent else Color.White,
                onClick = onFavorite
            )
            ControlIcon(Icons.Default.Settings, stringResource(R.string.player_settings_title), 26.dp, onClick = onSettings)
        }
    }
}

@Composable
private fun BottomBar(
    isLive: Boolean,
    position: Long,
    duration: Long,
    speed: Float,
    epg: List<EpgEntity>,
    tv: Boolean,
    seekFocus: FocusRequester,
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
            val barSource = remember { MutableInteractionSource() }
            val barFocused by barSource.collectIsFocusedAsState()
            var scrubbing by remember { mutableStateOf(false) }
            var target by remember { mutableLongStateOf(0L) }
            var moves by remember { mutableIntStateOf(0) }
            val commit by rememberUpdatedState(onSeekDone)

            fun finishScrub() {
                if (!scrubbing) return
                scrubbing = false
                commit()
            }

            // Mantener pulsado avanza cada vez más rápido; al soltar, el salto se aplica solo.
            LaunchedEffect(moves) {
                if (!scrubbing) return@LaunchedEffect
                delay(SCRUB_COMMIT_MS)
                finishScrub()
            }

            val barModifier = if (!tv) Modifier else Modifier
                .focusRequester(seekFocus)
                .onFocusChanged { if (!it.isFocused) finishScrub() }
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (event.type == KeyEventType.KeyDown) {
                                if (!scrubbing) {
                                    target = position
                                    scrubbing = true
                                }
                                val repeat = event.nativeKeyEvent.repeatCount
                                val step = when {
                                    repeat < 5 -> 10_000L
                                    repeat < 15 -> 30_000L
                                    else -> maxOf(60_000L, duration / 50)
                                }
                                target = (target + if (event.key == Key.DirectionLeft) -step else step).coerceIn(0L, duration)
                                onSeekChange(target)
                                moves++
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (event.type == KeyEventType.KeyDown) finishScrub()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = barSource)
                .clip(RoundedCornerShape(12.dp))
                .background(if (barFocused) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                .padding(horizontal = 8.dp)

            Box(barModifier) {
                Slider(
                    value = position.toFloat().coerceIn(0f, duration.toFloat()),
                    valueRange = 0f..duration.toFloat(),
                    onValueChange = { onSeekChange(it.toLong()) },
                    onValueChangeFinished = onSeekDone,
                    colors = SliderDefaults.colors(
                        thumbColor = if (barFocused) Color.White else EntreBrand.Accent,
                        activeTrackColor = EntreBrand.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = if (barFocused) 0.4f else 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                        .then(if (tv) Modifier.focusProperties { canFocus = false } else Modifier)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PlayerViewModel.formatMs(position),
                    color = if (scrubbing) EntreBrand.Accent else Color.White,
                    style = if (barFocused) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = if (scrubbing) FontWeight.Bold else null
                )
                if (barFocused) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.player_seek_hint),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
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
                    stringResource(R.string.player_now_program, current.title),
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
    val scale by animateFocusScale(focused, 1.12f, "controlScale")
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
                            onFeedback(
                                Feedback(
                                    Icons.Default.BrightnessMedium,
                                    context.getString(R.string.player_feedback_brightness, (level * 100).toInt()),
                                    level
                                )
                            )
                        } else {
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * maxVolume).toInt(), 0)
                            onFeedback(
                                Feedback(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    context.getString(R.string.player_feedback_volume, (level * 100).toInt()),
                                    level
                                )
                            )
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
