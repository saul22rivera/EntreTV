package com.entretv.app.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.entretv.app.R
import com.entretv.app.ui.ALL_ROW
import com.entretv.app.ui.MY_LIST_ROW
import com.entretv.app.ui.asString
import com.entretv.app.ui.rowLabel
import kotlinx.coroutines.delay

/** "Destacados" no tiene nombre de categoría (es null): esta es su clave en la columna. */
private const val FEATURED_KEY = "__featured"

/** Tiempo quieto sobre una categoría antes de cargarla: recorrer la lista no recalcula cada paso. */
private const val SELECT_DELAY_MS = 350L

/**
 * TV, Películas y Series: categorías en columna a la izquierda, como En vivo, con el contenido
 * a la derecha. Quedarse sobre una la muestra; OK la abre y lleva el foco al contenido. Las
 * bloqueadas solo se abren con OK (piden el PIN).
 */
@Composable
fun TvCategoryColumn(
    categories: List<String>,
    labels: Map<String, String>,
    counts: Map<String, Int>,
    locked: Set<String>,
    myListCount: Int,
    selected: String?,
    /** Cada aumento lleva el foco a la categoría elegida. */
    focusRequests: Int,
    onSelect: (String?) -> Unit,
    onOpenLocked: (String) -> Unit,
    onRight: () -> Unit,
    onLeft: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries: List<String?> = remember(categories) {
        buildList {
            add(null)
            add(ALL_ROW)
            add(MY_LIST_ROW)
            addAll(categories.filter { it != ALL_ROW && it != MY_LIST_ROW })
        }
    }
    val listState = rememberLazyListState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(name: String?) = requesters.getOrPut(name ?: FEATURED_KEY) { FocusRequester() }
    val currentSelected by rememberUpdatedState(selected)

    suspend fun reveal(state: LazyListState, index: Int) {
        if (state.layoutInfo.visibleItemsInfo.none { it.index == index && it.offset >= 0 }) {
            state.scrollToItem((index - 3).coerceAtLeast(0))
        }
        withFrameNanos { }
    }

    /** La fila recién desplazada puede tardar un frame más en componerse: se reintenta. */
    suspend fun focusSelected() {
        val index = entries.indexOf(currentSelected).coerceAtLeast(0)
        repeat(3) {
            reveal(listState, index)
            if (runCatching { requester(entries[index]).requestFocus() }.isSuccess) return
        }
    }

    LaunchedEffect(focusRequests) {
        if (focusRequests > 0) {
            withFrameNanos { }
            focusSelected()
        }
    }

    // Clave de la categoría enfocada; null = ninguna. Al quedarse quieto sobre ella se carga.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedKey) {
        val key = focusedKey ?: return@LaunchedEffect
        val name = if (key == FEATURED_KEY) null else key
        if (name == currentSelected || name in locked) return@LaunchedEffect
        delay(SELECT_DELAY_MS)
        onSelect(name)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = modifier
            .width(TV_CATEGORY_COLUMN_WIDTH)
            .fillMaxHeight()
            .onFocusChanged { onFocusChange(it.hasFocus) }
            // Actúa al bajar la tecla y consume también al soltarla.
            .onPreviewKeyEvent { event ->
                // Arriba de la primera categoría no hay nada: se queda ahí.
                if (event.key == Key.DirectionUp) {
                    return@onPreviewKeyEvent focusedKey == FEATURED_KEY
                }
                if (event.key != Key.DirectionRight && event.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.DirectionRight) onRight() else onLeft()
                }
                true
            }
    ) {
        itemsIndexed(entries, key = { _, name -> name ?: FEATURED_KEY }) { _, name ->
            val isLocked = name != null && name in locked
            LiveCategoryRow(
                label = when (name) {
                    null -> stringResource(R.string.menu_featured)
                    ALL_ROW -> stringResource(R.string.menu_all)
                    MY_LIST_ROW -> stringResource(R.string.menu_my_list)
                    else -> labels[name] ?: rowLabel(name).asString()
                },
                count = when (name) {
                    null -> 0
                    ALL_ROW -> counts.values.sum()
                    MY_LIST_ROW -> myListCount
                    else -> counts[name] ?: 0
                },
                selected = name == selected,
                locked = isLocked,
                favorite = name == MY_LIST_ROW,
                modifier = Modifier
                    .focusRequester(requester(name))
                    .onFocusChanged {
                        val key = name ?: FEATURED_KEY
                        if (it.isFocused) focusedKey = key else if (focusedKey == key) focusedKey = null
                    },
                onClick = {
                    if (isLocked) onOpenLocked(name!!)
                    else {
                        if (name != selected) onSelect(name)
                        onRight()
                    }
                }
            )
        }
    }
}
