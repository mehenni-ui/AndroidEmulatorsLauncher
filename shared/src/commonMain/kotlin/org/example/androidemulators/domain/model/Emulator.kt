package org.example.androidemulators.domain.model

data class Emulator(
    val name: String,
    val state: EmulatorState = EmulatorState.Stopped
)
