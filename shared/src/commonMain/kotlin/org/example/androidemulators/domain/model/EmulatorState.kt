package org.example.androidemulators.domain.model

sealed class EmulatorState {
    data object Stopped : EmulatorState()
    data object Starting : EmulatorState()
    data object Running : EmulatorState()
    data class Error(val message: String) : EmulatorState()
}
