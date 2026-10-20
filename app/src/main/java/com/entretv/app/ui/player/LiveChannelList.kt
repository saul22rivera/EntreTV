package com.entretv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.ui.theme.EntreBrand

/** Lista lateral de canales del mismo grupo, con lo que están pasando ahora. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveChannelList(
    visible: Boolean,
    channels: List<ChannelEntity>,
    currentId: String?,
    nowPlaying: suspend (ChannelEntity) -> String?,
    onSelect: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = visible, onBack = onDismiss)

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
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut()
        ) {
            val currentIndex = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

            Surface(
                color = EntreBrand.Panel.copy(alpha = 0.97f),
                shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .focusProperties { exit = { FocusRequester.Cancel } }
                    .focusGroup()
            ) {
                Column {
                    Text(
                        channels.firstOrNull()?.groupTitle?.ifBlank { null } ?: "Canales",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
                    )
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                            ChannelRow(
                                channel = channel,
                                position = index + 1,
                                current = channel.id == currentId,
                                nowPlaying = nowPlaying,
                                modifier = if (index == currentIndex) Modifier.focusRequester(focus) else Modifier,
                                onClick = { onSelect(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    position: Int,
    current: Boolean,
    nowPlaying: suspend (ChannelEntity) -> String?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val program by produceState<String?>(null, channel.id) { value = nowPlaying(channel) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.16f)
                    current -> EntreBrand.Accent.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(1.5.dp, EntreBrand.Accent, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            (channel.number.takeIf { it > 0 } ?: position).toString(),
            color = if (current) EntreBrand.Accent else EntreBrand.TextDim,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(36.dp)
        )
        Box(
            Modifier
                .size(width = 56.dp, height = 36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            program?.let {
                Text(
                    it,
                    color = EntreBrand.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
