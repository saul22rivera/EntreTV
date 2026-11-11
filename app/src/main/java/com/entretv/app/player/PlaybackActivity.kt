package com.entretv.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Si hay algo en el reproductor (pantalla completa o mini reproductor). Lo que usa la red en
 * segundo plano (buscar actualizaciones, la pantalla de inicio de la TV) espera a que termine.
 */
object PlaybackActivity {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    internal fun set(active: Boolean) {
        _active.value = active
    }

    suspend fun awaitIdle() {
        _active.first { !it }
    }
}
