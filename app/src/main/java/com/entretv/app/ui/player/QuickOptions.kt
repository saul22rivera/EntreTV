package com.entretv.app.ui.player

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entretv.app.ui.theme.EntreBrand

/** Una opción de las listas rápidas del reproductor. */
@Immutable
class QuickItem(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * Lista corta que flota encima de una pastilla del reproductor en TV. [anchorX] y [anchorTop]
 * son el borde izquierdo y el superior de la pastilla, en px del contenedor (que ocupa toda la
 * pantalla). Atrás, Izquierda o Derecha la cierran; elegir algo también.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QuickList(
    title: String,
    items: List<QuickItem>,
    anchorX: Int,
    anchorTop: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    val initial = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    Surface(
        color = EntreBrand.Panel.copy(alpha = 0.97f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        anchorX.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0)),
                        (anchorTop - placeable.height - gap).coerceAtLeast(0)
                    )
                }
            }
            .width(320.dp)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    // Como en el panel: en TV Atrás llega como salida de foco y se perdería.
                    Key.Back, Key.Escape, Key.DirectionLeft, Key.DirectionRight -> {
                        if (event.type == KeyEventType.KeyDown) onDismiss()
                        true
                    }
                    else -> false
                }
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            item {
                Text(
                    title,
                    color = EntreBrand.TextDim,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            itemsIndexed(items) { i, item ->
                PanelRow(
                    title = item.label,
                    selected = item.selected,
                    modifier = if (i == initial) Modifier.focusRequester(focus) else Modifier,
                    onClick = {
                        item.onClick()
                        onDismiss()
                    }
                )
            }
        }
    }
}
