package com.emilflach.groceries

import androidx.compose.ui.window.ComposeUIViewController
import com.emilflach.groceries.data.SqlDriverFactory
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalImportRepository

@Suppress("unused")
fun ViewController() = ComposeUIViewController {
    App(
        sqlDriverFactory = SqlDriverFactory(),
        lokcalCatalogReader = LokcalCatalogReader(),
        lokcalImportRepository = LokcalImportRepository(),
    )
}
