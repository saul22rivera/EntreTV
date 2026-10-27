package com.entretv.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Si hay alguna pantalla de la app a la vista. Lo lleva [com.entretv.app.EntreTVApp]. */
object AppForeground {
    /** Solo se cambia en el hilo principal. */
    @Volatile private var started = 0

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    val isForeground: Boolean get() = started > 0

    fun onActivityStarted() {
        started++
        _visible.value = true
    }

    fun onActivityStopped() {
        if (started > 0) started--
        _visible.value = started > 0
    }
}
