package com.emilflach.groceries

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Groceries", alwaysOnTop = true) {
        App()
    }
}
