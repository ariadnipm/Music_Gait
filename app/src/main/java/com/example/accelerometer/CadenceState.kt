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

    /** Update cadence + walking flag */
    fun updateCadence(cadenceHz: Double, isWalking: Boolean) {
        _state.value = _state.value.copy(
            cadenceHz = cadenceHz,
            isWalking = isWalking
        )
    }

    /** Called from UI when user starts/stops the service */
    fun setRunning(running: Boolean) {
        if (!running) {

            _state.value = UiCadenceState(cadenceHz = 0.0, isWalking = false, isRunning = false)
        } else {
            _state.value = _state.value.copy(isRunning = true)
        }
    }

    /** Optional manual reset */
    fun reset() {
        _state.value = UiCadenceState()
    }
}
