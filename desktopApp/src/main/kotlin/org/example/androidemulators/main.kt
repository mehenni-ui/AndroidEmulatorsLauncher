package org.example.androidemulators

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Android Emulator Launcher",
    ) {
        App()
    }
}