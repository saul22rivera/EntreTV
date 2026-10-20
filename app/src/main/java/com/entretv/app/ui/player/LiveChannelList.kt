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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.ui.LiveCategory
import com.entretv.app.ui.theme.EntreBrand

/** Lo que necesita el navegador de canales; lo arma la pantalla del reproductor. */
class ChannelBrowserState(
    val categories: List<LiveCategory>,
    val category: String,
    val query: String,
    val channels: List<ChannelEntity>,
    val currentId: String?,
    val nowPlaying: suspend (ChannelEntity) -> String?,
    val onCategory: (String) -> Unit,
    val onQuery: (String) -> Unit,
    val onSelect: (ChannelEntity) -> Unit
)

/**
 * Panel lateral sobre el video (TV y celular acostado): categorías a la izquierda,
 * canales con búsqueda a la derecha. El video sigue reproduciéndose detrás.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveChannelList(
    visible: Boolean,
    state: ChannelBrowserState,
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
            BoxWithConstraints {
                val twoColumns = maxWidth >= 560.dp
                Surface(
                    color = EntreBrand.Panel.copy(alpha = 0.97f),
                    shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (twoColumns) 640.dp else 360.dp)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusGroup()
                ) {
                    ChannelBrowser(state, twoColumns = twoColumns, focusCurrent = true)
                }
            }
        }
    }
}

/**
 * Categorías + búsqueda + lista. Con [twoColumns] las categorías van en columna (TV);
 * si no, en una fila de chips arriba (celular). [focusCurrent] pone el foco en el canal actual.
 */
@Composable
fun ChannelBrowser(
    state: ChannelBrowserState,
    twoColumns: Boolean,
    focusCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    if (twoColumns) {
        Row(modifier.fillMaxSize()) {
            CategoryColumn(state, Modifier.width(230.dp).fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.08f)))
            ChannelColumn(state, focusCurrent, inlineSearch = false, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                state = rememberLazyListState(
                    initialFirstVisibleItemIndex = state.categories.indexOfFirst { it.raw == state.category }.coerceAtLeast(0)
                )
            ) {
                items(state.categories, key = { it.raw }) { category ->
                    CategoryChip(category, selected = category.raw == state.category && state.query.isEmpty()) {
                        state.onCategory(category.raw)
                    }
                }
            }
            ChannelColumn(state, focusCurrent, inlineSearch = true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategoryColumn(state: ChannelBrowserState, modifier: Modifier) {
    val selectedIndex = state.categories.indexOfFirst { it.raw == state.category }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    Column(modifier) {
        Text(
            "Categorías",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            items(state.categories, key = { it.raw }) { category ->
                CategoryRow(category, selected = category.raw == state.category && state.query.isEmpty()) {
                    state.onCategory(category.raw)
                }
            }
        }
    }
}

@Composable
private fun ChannelColumn(state: ChannelBrowserState, focusCurrent: Boolean, inlineSearch: Boolean, modifier: Modifier) {
    val channels = state.channels
    val currentIndex = channels.indexOfFirst { it.id == state.currentId }
    val listState = remember(state.category, state.query.isEmpty()) {
        androidx.compose.foundation.lazy.LazyListState((currentIndex - 2).coerceAtLeast(0))
    }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(channels.isNotEmpty()) {
        if (focusCurrent && !focusedOnce && channels.isNotEmpty()) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
            focusedOnce = true
        }
    }
    val title = when {
        state.query.isNotBlank() -> "Resultados (${channels.size})"
        else -> state.categories.firstOrNull { it.raw == state.category }?.label ?: "Canales"
    }
    var searching by remember { mutableStateOf(false) }
    Column(modifier) {
        if (inlineSearch) {
            SearchField(
                value = state.query,
                onValueChange = state.onQuery,
                onDone = { focusManager.clearFocus() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp)
            )
        } else {
            // En TV un campo de texto enfocable abre el teclado con solo pasar por encima.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp)
            ) {
                SearchButton(
                    label = state.query.ifBlank { "Buscar canal" },
                    onClick = { searching = true },
                    modifier = Modifier.weight(1f)
                )
                if (state.query.isNotBlank()) {
                    IconButton(onClick = { state.onQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda", tint = Color.White)
                    }
                }
            }
        }
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)
        )
        if (channels.isEmpty()) {
            Text(
                if (state.query.isNotBlank()) "No hay canales con ese nombre." else "No hay canales en esta categoría.",
                color = EntreBrand.TextDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                ChannelRow(
                    channel = channel,
                    position = index + 1,
                    current = channel.id == state.currentId,
                    nowPlaying = state.nowPlaying,
                    modifier = if (index == currentIndex.coerceAtLeast(0)) Modifier.focusRequester(focus) else Modifier,
                    onClick = { state.onSelect(channel) }
                )
            }
        }
    }

    if (searching) {
        var draft by remember { mutableStateOf(state.query) }
        val fieldFocus = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { searching = false },
            containerColor = EntreBrand.Panel,
            title = { Text("Buscar canal", color = Color.White) },
            text = {
                SearchField(
                    value = draft,
                    onValueChange = { draft = it },
                    onDone = {
                        state.onQuery(draft)
                        searching = false
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.onQuery(draft)
                    searching = false
                }) { Text("Buscar", color = EntreBrand.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { searching = false }) { Text("Cancelar", color = Color.White) }
            }
        )
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { fieldFocus.requestFocus() }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("Buscar canal") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EntreBrand.Accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = EntreBrand.Accent
        ),
        modifier = modifier
    )
}

@Composable
private fun SearchButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f))
            .border(1.5.dp, if (focused) EntreBrand.Accent else Color.White.copy(alpha = 0.15f), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = EntreBrand.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = EntreBrand.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryRow(category: LiveCategory, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.16f)
                    selected -> EntreBrand.Accent.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(1.5.dp, EntreBrand.Accent, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            category.label,
            color = if (selected) Color.White else EntreBrand.Text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(category.count.toString(), color = EntreBrand.TextDim, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CategoryChip(category: LiveCategory, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Text(
        category.label,
        color = if (selected) Color.White else EntreBrand.Text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) EntreBrand.Accent else Color.White.copy(alpha = 0.10f))
            .then(if (focused) Modifier.border(1.5.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
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
