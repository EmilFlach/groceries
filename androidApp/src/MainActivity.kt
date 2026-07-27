package com.emilflach.groceries

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.emilflach.groceries.data.SqlDriverFactory
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalImportRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        setContent {
            App(
                sqlDriverFactory = SqlDriverFactory(applicationContext),
                lokcalCatalogReader = LokcalCatalogReader(applicationContext),
                lokcalImportRepository = LokcalImportRepository(applicationContext),
            )
        }
    }
}
