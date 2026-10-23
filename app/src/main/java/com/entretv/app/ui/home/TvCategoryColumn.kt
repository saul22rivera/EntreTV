package com.entretv.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.entretv.app.ui.SortOrder
import com.entretv.app.ui.asString
import com.entretv.app.ui.label
import com.entretv.app.ui.rowLabel
import kotlinx.coroutines.delay

/** "Destacados" no tiene nombre de categoría (es null): esta es su clave en la columna. */
private const val FEATURED_KEY = "__featured"

/** Clave de la fila Ordenar, arriba de todo en las columnas de categorías. */
internal const val SORT_KEY = "__sort"

/** Tiempo quieto sobre una categoría antes de cargarla: recorrer la lista no recalcula cada paso. */
private const val SELECT_DELAY_MS = 350L

/** Menú de órdenes de la cabecera y de la fila Ordenar. Con [focusCurrent] arranca sobre el orden actual. */
@Composable
internal fun SortMenu(
    expanded: Boolean,
    options: List<SortOrder>,
    sort: SortOrder?,
    focusCurrent: Boolean,
    onSort: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val current = remember { FocusRequester() }
        val start = options.firstOrNull { it == sort } ?: options.firstOrNull()
        options.forEach { order ->
            DropdownMenuItem(
                text = { Text(order.label) },
                leadingIcon = if (sort == order) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null,
                onClick = { onDismiss(); onSort(order) },
                modifier = if (focusCurrent && order == start) Modifier.focusRequester(current) else Modifier
            )
        }
        if (focusCurrent) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { current.requestFocus() }
            }
        }
    }
}

/** Fila "Ordenar" de las columnas de categorías en TV: abre el mismo menú que la cabecera del celular. */
@Composable
internal fun TvSortRow(
    options: List<SortOrder>,
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    // En En vivo puntuación y recientes no aplican: ahí rige el original.
    val shown = sort.takeIf { it in options } ?: SortOrder.ORIGINAL
    Box(Modifier.fillMaxWidth()) {
        LiveCategoryRow(
            label = stringResource(R.string.home_sort),
            count = 0,
            selected = false,
            locked = false,
            favorite = false,
            icon = Icons.AutoMirrored.Filled.Sort,
            value = shown.label,
            modifier = modifier,
            onClick = { open = true }
        )
        SortMenu(
            expanded = open,
            options = options,
            sort = shown,
            focusCurrent = true,
            onSort = onSort,
            onDismiss = { open = false }
        )
    }
}

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
    /** Quieto sobre una categoría: recibe las vecinas; se cancela al mover el foco. */
    onRest: suspend (List<String>) -> Unit,
    onOpenLocked: (String) -> Unit,
    onRight: () -> Unit,
    onLeft: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Órdenes posibles; con menos de dos no hay fila Ordenar. */
    sortOptions: List<SortOrder> = emptyList(),
    sort: SortOrder = SortOrder.ORIGINAL,
    onSort: (SortOrder) -> Unit = {}
) {
    val showSort = sortOptions.size > 1
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
            reveal(listState, if (showSort) index + 1 else index)
            if (runCatching { requester(entries[index]).requestFocus() }.isSuccess) return
        }
    }

    LaunchedEffect(focusRequests) {
        if (focusRequests > 0) {
            withFrameNanos { }
            focusSelected()
        }
    }

    // Clave de la categoría enfocada; null = ninguna. Al quedarse quieto sobre ella se carga y
    // se precargan las de arriba y abajo.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedKey) {
        val key = focusedKey?.takeIf { it != SORT_KEY } ?: return@LaunchedEffect
        val name = if (key == FEATURED_KEY) null else key
        delay(SELECT_DELAY_MS)
        if (name != currentSelected && name !in locked) onSelect(name)
        val index = entries.indexOf(name)
        onRest(
            listOfNotNull(entries.getOrNull(index - 1), entries.getOrNull(index + 1))
                .filter { it != ALL_ROW && it != MY_LIST_ROW }
        )
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
                // Arriba de la primera fila no hay nada: se queda ahí.
                if (event.key == Key.DirectionUp) {
                    return@onPreviewKeyEvent focusedKey == if (showSort) SORT_KEY else FEATURED_KEY
                }
                if (event.key != Key.DirectionRight && event.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.DirectionRight) onRight() else onLeft()
                }
                true
            }
    ) {
        if (showSort) {
            item(key = SORT_KEY) {
                TvSortRow(
                    options = sortOptions,
                    sort = sort,
                    onSort = onSort,
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) focusedKey = SORT_KEY else if (focusedKey == SORT_KEY) focusedKey = null
                    }
                )
            }
        }
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
