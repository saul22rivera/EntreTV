package com.entretv.app.data

/** Si hay alguna pantalla de la app a la vista. Lo lleva [com.entretv.app.EntreTVApp]. */
object AppForeground {
    /** Solo se cambia en el hilo principal. */
    @Volatile private var started = 0

    val isForeground: Boolean get() = started > 0

    fun onActivityStarted() {
        started++
    }

    fun onActivityStopped() {
        if (started > 0) started--
    }
}
