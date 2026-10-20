package com.entretv.app.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import kotlin.math.abs

/**
 * Flechas arriba/abajo del control en las filas del Inicio (TV). Van al elemento de la fila
 * vecina que está a la misma altura en pantalla y esa fila queda arriba, siempre igual.
 * La búsqueda de foco de Compose decide por geometría entre filas con distinto desplazamiento
 * y anchos, y saltaba a otro índice o al "Ver todo" del encabezado.
 *
 * Cada fila es un ítem del LazyColumn con la clave de [rowOrder]; sus elementos se registran
 * con [tvNavItem].
 */
@Stable
class TvCatalogNav {
    data class Slot(val row: String, val index: Int)

    /** Claves de las filas, en el orden en que están en el LazyColumn. */
    var rowOrder: List<String> = emptyList()
    var columnState: LazyListState? = null

    private val rows = HashMap<String, LazyListState>()
    private val requesters = HashMap<Slot, FocusRequester>()

    /** Elemento de fila con el foco ahora; null si el foco está en otra cosa. */
    var focused: Slot? = null
        private set

    /** Último elemento de fila enfocado: a él vuelve el foco al entrar al catálogo. */
    var last: Slot? = null
        private set

    private var busy = false

    fun registerRow(row: String, state: LazyListState) {
        rows[row] = state
    }

    fun unregisterRow(row: String, state: LazyListState) {
        if (rows[row] === state) rows.remove(row)
    }

    internal fun put(slot: Slot, requester: FocusRequester) {
        requesters[slot] = requester
    }

    internal fun remove(slot: Slot, requester: FocusRequester) {
        if (requesters[slot] === requester) requesters.remove(slot)
    }

    internal fun onItemFocus(slot: Slot, isFocused: Boolean) {
        if (isFocused) {
            focused = slot
            last = slot
        } else if (focused == slot) {
            focused = null
        }
    }

    /** Requester del último elemento enfocado, si sigue en pantalla. */
    fun lastRequester(): FocusRequester? = last?.let { requesters[it] }

    /** Olvida la posición (otra sección u otra vista). */
    fun reset() {
        last = null
        focused = null
    }

    /** true si [key] lo resuelve [move] o hay que tragarla (derecha en el último de la fila). */
    fun handles(key: Key): Boolean {
        val current = focused ?: return false
        if (current.row !in rowOrder) return false
        return when (key) {
            Key.DirectionUp, Key.DirectionDown -> true
            Key.DirectionRight -> {
                val total = rows[current.row]?.layoutInfo?.totalItemsCount ?: return false
                current.index >= total - 1
            }
            else -> false
        }
    }

    /**
     * Mueve el foco una fila arriba ([dir] = -1) o abajo (+1). Arriba desde la primera fila
     * llama a [onTopExit].
     */
    suspend fun move(dir: Int, onTopExit: () -> Unit) {
        if (busy || dir == 0) return
        val current = focused ?: return
        val order = rowOrder
        val from = order.indexOf(current.row)
        if (from < 0) return
        val to = from + dir
        if (to !in order.indices) {
            if (dir < 0) onTopExit()
            return
        }
        busy = true
        try {
            val targetRow = order[to]
            val column = columnState
            val currentState = rows[current.row]
            val currentInfo = currentState?.layoutInfo?.visibleItemsInfo?.firstOrNull { it.index == current.index }
            val centerX = currentInfo?.let { it.offset + it.size / 2 }
            val visibleColumn = current.index - (currentState?.firstVisibleItemIndex ?: 0)

            // La fila de destino tiene que estar compuesta para conocer sus elementos.
            if (column != null && column.layoutInfo.visibleItemsInfo.none { it.key == targetRow }) {
                column.scrollToItem(to)
                withFrameNanos { }
            }
            val targetState = rows[targetRow] ?: return
            val total = targetState.layoutInfo.totalItemsCount
            if (total == 0) return
            val visible = targetState.layoutInfo.visibleItemsInfo
            val index = when {
                centerX != null && visible.isNotEmpty() ->
                    visible.minBy { abs(it.offset + it.size / 2 - centerX) }.index
                else -> targetState.firstVisibleItemIndex + visibleColumn
            }.coerceIn(0, total - 1)

            var requester = requesters[Slot(targetRow, index)]
            if (requester == null) {
                targetState.scrollToItem(index)
                withFrameNanos { }
                requester = requesters[Slot(targetRow, index)]
            }
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                // La fila enfocada queda arriba, con su título a la vista.
                column?.animateScrollToItem(to)
            }
        } finally {
            busy = false
        }
    }
}

/** Registra un elemento de fila (o de la cuadrícula, con su propia clave) en [nav]. */
@Composable
fun Modifier.tvNavItem(nav: TvCatalogNav?, row: String, index: Int): Modifier {
    val requester = remember { FocusRequester() }
    val slot = remember(row, index) { TvCatalogNav.Slot(row, index) }
    if (nav != null) {
        DisposableEffect(nav, slot, requester) {
            nav.put(slot, requester)
            onDispose { nav.remove(slot, requester) }
        }
    }
    return if (nav == null) this
    else this.focusRequester(requester).onFocusChanged { nav.onItemFocus(slot, it.isFocused) }
}
