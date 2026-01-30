package com.example.accelerometer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiCadenceState(
    val cadenceHz: Double = 0.0,
    val isWalking: Boolean = false,
    val isRunning: Boolean = false
)

object CadenceState {
    private val _state = MutableStateFlow(UiCadenceState())
    val state: StateFlow<UiCadenceState> = _state.asStateFlow()

    /** Update cadence + walking flag (called from processing/service). */
    fun updateCadence(cadenceHz: Double, isWalking: Boolean) {
        val safeHz = cadenceHz.coerceAtLeast(0.0)
        _state.value = _state.value.copy(
            cadenceHz = safeHz,
            isWalking = isWalking
        )
    }

    /** Called when the service actually starts/stops (and from UI when requested). */
    fun setRunning(running: Boolean) {
        _state.value = if (running) {
            _state.value.copy(isRunning = true)
        } else {
            // reset UI values when stopped
            UiCadenceState(cadenceHz = 0.0, isWalking = false, isRunning = false)
        }
    }

    /** Optional manual reset. */
    fun reset() {
        _state.value = UiCadenceState()
    }
}
