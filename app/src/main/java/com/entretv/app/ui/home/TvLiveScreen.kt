package com.entretv.app.ui.home

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.entretv.app.R
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.EpgEntity
import com.entretv.app.ui.LiveGuide
import com.entretv.app.ui.MY_LIST_ROW
import com.entretv.app.ui.TvLiveViewModel
import com.entretv.app.ui.asString
import com.entretv.app.ui.rowLabel
import com.entretv.app.ui.theme.EntreBrand
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LiveZone { CATEGORIES, CHANNELS, PLAYER }

/**
 * En vivo en TV: categorías, canales y a la derecha el mini reproductor con la guía del
 * canal enfocado. Recorrer no cambia lo que suena; OK reproduce y OK sobre el canal que
 * suena abre la pantalla completa sin recargarlo.
 *
 * Las flechas entre columnas se manejan a mano: la búsqueda de foco por geometría salta a
 * la fila que quede enfrente, no a la categoría elegida ni al canal que suena.
 */
@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvLiveScreen(
    playlistId: String?,
    categories: List<String>,
    categoryLabels: Map<String, String>,
    lockedCategories: Set<String>,
    onOpenMenu: () -> Unit,
    onUnlock: () -> Unit,
    onFullscreen: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvLiveViewModel = hiltViewModel()
) {
    LaunchedEffect(playlistId) { viewModel.setPlaylist(playlistId) }
    val channels by viewModel.channels.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val chosenCategory by viewModel.category.collectAsState()
    val playing by viewModel.playing.collectAsState()
    val guide by viewModel.guide.collectAsState()
    val buffering by viewModel.playerManager.isBuffering.collectAsState()
    val scope = rememberCoroutineScope()

    val allowedGroups = remember(categories, lockedCategories) { categories.filter { it !in lockedCategories }.toSet() }
    val favorites = remember(favoriteIds, channels, allowedGroups) {
        favoriteIds.mapNotNull { channels.byId[it] }.filter { it.groupTitle in allowedGroups }
    }
    val cats = remember(categories, channels, favorites, lockedCategories) {
        buildList {
            if (favorites.isNotEmpty()) add(MY_LIST_ROW)
            addAll(categories.filter { it in lockedCategories || channels.byGroup[it].orEmpty().isNotEmpty() })
        }
    }
    val selected = chosenCategory?.takeIf { it in cats }
        ?: playing?.groupTitle?.takeIf { it in cats }
        ?: cats.firstOrNull()
    val list = remember(selected, channels, favorites, lockedCategories) {
        when (selected) {
            null -> emptyList()
            MY_LIST_ROW -> favorites
            in lockedCategories -> emptyList()
            else -> channels.byGroup[selected].orEmpty()
        }
    }

    var zone by rememberSaveable { mutableStateOf(LiveZone.CHANNELS) }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val catState = rememberLazyListState()
    val chState = rememberLazyListState()
    val catFocus = remember { HashMap<String, FocusRequester>() }
    val chFocus = remember { HashMap<String, FocusRequester>() }
    val playerFocus = remember { FocusRequester() }

    fun catRequester(name: String) = catFocus.getOrPut(name) { FocusRequester() }
    fun chRequester(id: String) = chFocus.getOrPut(id) { FocusRequester() }

    suspend fun reveal(state: LazyListState, index: Int) {
        if (state.layoutInfo.visibleItemsInfo.none { it.index == index && it.offset >= 0 }) {
            state.scrollToItem((index - 3).coerceAtLeast(0))
        }
        withFrameNanos { }
    }

    fun focusCategory() {
        val name = selected ?: return
        scope.launch {
            reveal(catState, cats.indexOf(name).coerceAtLeast(0))
            runCatching { catRequester(name).requestFocus() }
        }
    }

    fun focusChannel() {
        if (list.isEmpty()) return
        val target = list.firstOrNull { it.id == focusedId }
            ?: list.firstOrNull { it.id == playing?.id }
            ?: list.first()
        scope.launch {
            reveal(chState, list.indexOf(target))
            runCatching { chRequester(target.id).requestFocus() }
        }
    }

    fun focusPlayer() {
        scope.launch {
            withFrameNanos { }
            runCatching { playerFocus.requestFocus() }
        }
    }

    fun focusZone() {
        when {
            zone == LiveZone.PLAYER && playing != null -> focusPlayer()
            zone == LiveZone.CATEGORIES || list.isEmpty() -> focusCategory()
            else -> focusChannel()
        }
    }

    // Otra categoría: la lista arranca arriba (o en el canal que suena si está ahí).
    LaunchedEffect(selected) {
        val index = list.indexOfFirst { it.id == playing?.id }
        chState.scrollToItem((index - 3).coerceAtLeast(0))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resume()
                Lifecycle.Event.ON_STOP -> if (!viewModel.openingFullscreen) viewModel.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Salir de En vivo corta el canal; abrir la pantalla completa no.
            if (!viewModel.openingFullscreen) viewModel.stop()
        }
    }

    fun openFullscreen(channel: ChannelEntity) {
        viewModel.openingFullscreen = true
        onFullscreen(channel)
    }

    /** Actúa al bajar la tecla, pero consume también al soltarla: si no, Atrás cerraría la app. */
    fun Modifier.keys(consumed: Set<Key>, onKey: (Key) -> Unit): Modifier = onPreviewKeyEvent { event ->
        if (event.key !in consumed) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) onKey(event.key)
        true
    }

    Row(
        modifier = modifier
            .focusProperties {
                enter = {
                    focusZone()
                    FocusRequester.Cancel
                }
            }
            .focusGroup()
            .padding(start = 12.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------------- Categorías ----------------
        LazyColumn(
            state = catState,
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier
                .width(210.dp)
                .fillMaxHeight()
                .keys(setOf(Key.DirectionRight, Key.DirectionLeft)) { key ->
                    if (key == Key.DirectionRight) focusChannel() else onOpenMenu()
                }
        ) {
            itemsIndexed(cats, key = { _, c -> c }) { _, name ->
                val locked = name in lockedCategories
                LiveCategoryRow(
                    label = if (name == MY_LIST_ROW) stringResource(R.string.menu_my_list) else categoryLabels[name] ?: rowLabel(name).asString(),
                    count = if (name == MY_LIST_ROW) favorites.size else channels.byGroup[name]?.size ?: 0,
                    selected = name == selected,
                    locked = locked,
                    favorite = name == MY_LIST_ROW,
                    modifier = Modifier
                        .focusRequester(catRequester(name))
                        .onFocusChanged {
                            if (it.isFocused) {
                                zone = LiveZone.CATEGORIES
                                if (name != selected) {
                                    focusedId = null
                                    viewModel.selectCategory(name)
                                }
                            }
                        },
                    onClick = { if (locked) onUnlock() else focusChannel() }
                )
            }
        }

        // ---------------- Canales ----------------
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                cats.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                selected in lockedCategories -> LiveHint(
                    icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = EntreBrand.TextDim) },
                    text = stringResource(R.string.live_locked_hint)
                )
                list.isEmpty() -> LiveHint(text = stringResource(R.string.live_no_channels))
                else -> LazyColumn(
                    state = chState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .keys(setOf(Key.DirectionLeft, Key.DirectionRight, Key.Back, Key.Escape)) { key ->
                            if (key == Key.DirectionRight) {
                                if (playing != null) focusPlayer()
                            } else focusCategory()
                        }
                ) {
                    itemsIndexed(list, key = { _, ch -> ch.id }) { _, channel ->
                        LiveChannelRow(
                            channel = channel,
                            playing = channel.id == playing?.id,
                            modifier = Modifier
                                .focusRequester(chRequester(channel.id))
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        zone = LiveZone.CHANNELS
                                        focusedId = channel.id
                                        viewModel.focus(channel)
                                    }
                                },
                            onClick = {
                                if (channel.id == playing?.id) openFullscreen(channel) else viewModel.play(channel)
                            }
                        )
                    }
                }
            }
        }

        // ---------------- Mini reproductor y guía ----------------
        Column(Modifier.width(400.dp).fillMaxHeight()) {
            MiniPlayer(
                viewModel = viewModel,
                playing = playing,
                buffering = buffering,
                modifier = Modifier
                    .focusRequester(playerFocus)
                    .onFocusChanged {
                        if (it.isFocused) {
                            zone = LiveZone.PLAYER
                            playing?.let(viewModel::focus)
                        }
                    }
                    .keys(setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionDown, Key.Back, Key.Escape)) { key ->
                        if (key == Key.DirectionLeft || key == Key.Back || key == Key.Escape) {
                            if (list.isEmpty()) focusCategory() else focusChannel()
                        }
                    },
                onClick = { playing?.let(::openFullscreen) }
            )
            Spacer(Modifier.height(12.dp))
            val guideChannel = if (zone == LiveZone.PLAYER) playing else list.firstOrNull { it.id == focusedId } ?: playing
            GuidePanel(channel = guideChannel, guide = guide)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MiniPlayer(
    viewModel: TvLiveViewModel,
    playing: ChannelEntity?,
    buffering: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color.Black)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.12f), shape)
            .clickable(interactionSource = source, indication = null, enabled = playing != null, onClick = onClick)
    ) {
        if (playing != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        player = viewModel.playerManager.player
                    }
                },
                update = { it.keepScreenOn = true },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), strokeWidth = 3.dp)
            if (focused) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.live_fullscreen_hint), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EntreBrand.TextDim, modifier = Modifier.size(40.dp))
                Text(
                    stringResource(R.string.live_pick_channel),
                    color = EntreBrand.TextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun GuidePanel(channel: ChannelEntity?, guide: LiveGuide) {
    if (channel == null) return
    val context = LocalContext.current
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    fun time(seconds: Long) = timeFormat.format(Date(seconds * 1000))

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel, Modifier.size(width = 48.dp, height = 32.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                channel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        val programs = if (guide.channelId == channel.id) guide.programs.filter { it.end > now } else emptyList()
        when {
            programs.isEmpty() && (guide.channelId != channel.id || guide.loading) ->
                Text(stringResource(R.string.live_guide_loading), color = EntreBrand.TextDim, style = MaterialTheme.typography.bodyMedium)
            programs.isEmpty() ->
                Text(stringResource(R.string.live_guide_empty), color = EntreBrand.TextDim, style = MaterialTheme.typography.bodyMedium)
            else -> {
                val current = programs.first().takeIf { it.start <= now }
                if (current != null) {
                    GuideLabel(stringResource(R.string.live_now))
                    Text(current.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("${time(current.start)} – ${time(current.end)}", color = EntreBrand.TextDim, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(10.dp))
                        val span = (current.end - current.start).coerceAtLeast(1)
                        LinearProgressIndicator(
                            progress = { ((now - current.start).toFloat() / span).coerceIn(0f, 1f) },
                            color = EntreBrand.Accent,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                    if (current.description.isNotBlank()) {
                        Text(current.description, color = EntreBrand.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                val upcoming = programs.drop(if (current != null) 1 else 0).take(2)
                upcoming.forEachIndexed { index, p ->
                    Spacer(Modifier.height(8.dp))
                    GuideLabel(stringResource(if (index == 0) R.string.live_next else R.string.live_later))
                    Row {
                        Text(time(p.start), color = EntreBrand.TextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
                        Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideLabel(text: String) {
    Text(text, color = EntreBrand.Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun LiveHint(text: String, icon: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        icon?.invoke()
        Spacer(Modifier.height(8.dp))
        Text(text, color = EntreBrand.TextDim, style = MaterialTheme.typography.bodyMedium)
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
            Text(channel.name.take(2).uppercase(), color = EntreBrand.TextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LiveCategoryRow(
    label: String,
    count: Int,
    selected: Boolean,
    locked: Boolean,
    favorite: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val content = when {
        focused -> EntreBrand.OnAccent
        selected -> EntreBrand.Accent
        else -> Color.White
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(
                when {
                    focused -> EntreBrand.Accent
                    selected -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
                shape
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        if (favorite || locked) {
            Icon(
                if (locked) Icons.Default.Lock else Icons.Default.Favorite,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = content,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$count", color = content.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LiveChannelRow(
    channel: ChannelEntity,
    playing: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val content = when {
        focused -> EntreBrand.OnAccent
        playing -> EntreBrand.Accent
        else -> Color.White
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(
                when {
                    focused -> EntreBrand.Accent
                    playing -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
                shape
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            if (channel.number > 0) "${channel.number}" else "",
            color = content.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(34.dp)
        )
        ChannelLogo(channel, Modifier.size(width = 40.dp, height = 28.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            channel.name,
            color = content,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = if (playing || focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (playing) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.live_playing), tint = content, modifier = Modifier.size(16.dp))
        }
    }
}
