package com.entretv.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.entretv.app.data.net.SpeedResult
import com.entretv.app.data.net.SpeedTester
import com.entretv.app.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SpeedTestState {
    /** [phase]: 0 = tu internet, 1 = servidor del proveedor. */
    data class Running(val phase: Int) : SpeedTestState
    data class Done(val result: SpeedResult) : SpeedTestState
}

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    private val tester: SpeedTester,
    private val player: PlayerManager
) : ViewModel() {

    private val _state = MutableStateFlow<SpeedTestState?>(null)
    val state: StateFlow<SpeedTestState?> = _state
    private var job: Job? = null

    fun start() {
        job?.cancel()
        // Lo que suena ocuparía la línea (y muchos proveedores aceptan una sola conexión).
        if (player.player.playbackState != Player.STATE_IDLE) player.stop()
        _state.value = SpeedTestState.Running(0)
        job = viewModelScope.launch {
            val result = tester.run { phase -> _state.value = SpeedTestState.Running(phase) }
            _state.value = SpeedTestState.Done(result)
        }
    }

    fun close() {
        job?.cancel()
        job = null
        _state.value = null
    }
}
