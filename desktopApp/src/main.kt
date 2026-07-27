package com.emilflach.groceries

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.emilflach.groceries.data.SqlDriverFactory
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalImportRepository
import io.github.vinceglb.filekit.FileKit

fun main() = application {
    FileKit.init(appId = "Groceries")
    Window(onCloseRequest = ::exitApplication, title = "Groceries", alwaysOnTop = true) {
        App(
            sqlDriverFactory = SqlDriverFactory(),
            lokcalCatalogReader = LokcalCatalogReader(),
            lokcalImportRepository = LokcalImportRepository(),
        )
    }
}
