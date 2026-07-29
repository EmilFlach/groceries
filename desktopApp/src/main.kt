package com.emilflach.groceries

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.emilflach.groceries.data.SqlDriverFactory
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalImportRepository
import io.github.vinceglb.filekit.FileKit

fun main() = application {
    FileKit.init(appId = "Groceries")
    Window(
        onCloseRequest = ::exitApplication,
        title = "Groceries",
        alwaysOnTop = true,
        state = rememberWindowState(
            position = WindowPosition(50.dp, 50.dp),
            width = 450.dp,
            height = 1000.dp
        )
    ) {
        App(
            sqlDriverFactory = SqlDriverFactory(),
            lokcalCatalogReader = LokcalCatalogReader(),
            lokcalImportRepository = LokcalImportRepository(),
        )
    }
}
