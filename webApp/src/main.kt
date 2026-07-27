package com.emilflach.groceries

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.emilflach.groceries.data.SqlDriverFactory
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalImportRepository
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App(
            sqlDriverFactory = SqlDriverFactory(),
            lokcalCatalogReader = LokcalCatalogReader(),
            lokcalImportRepository = LokcalImportRepository(),
        )
    }
}
