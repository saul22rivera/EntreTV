package com.entretv.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** La pantalla de bienvenida se mantiene hasta que Inicio tiene algo para mostrar. */
object AppStartup {
    /** Tope para no dejar al usuario mirando el logo si algo tarda de más. */
    const val MAX_WAIT_MS = 3_500L

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun markReady() {
        _ready.value = true
    }
}
