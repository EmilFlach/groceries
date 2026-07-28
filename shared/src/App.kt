package com.emilflach.groceries

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.emilflach.groceries.data.ShoppingListRepository
import com.emilflach.groceries.data.SqlDriverFactory
import com.emilflach.groceries.data.createDatabase
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalImportRepository
import com.emilflach.groceries.ui.components.AddItemSheet
import com.emilflach.groceries.ui.screens.LokcalSetupScreen
import com.emilflach.groceries.ui.screens.ShoppingListScreen
import com.emilflach.groceries.ui.theme.AppTheme
import com.emilflach.groceries.ui.util.ConfigureCoilImageLoader
import com.emilflach.groceries.viewmodel.LokcalSetupViewModel
import com.emilflach.groceries.viewmodel.ShoppingListViewModel

private enum class Screen { ShoppingList, LokcalSetup }

@Composable
fun App(
    sqlDriverFactory: SqlDriverFactory,
    lokcalCatalogReader: LokcalCatalogReader,
    lokcalImportRepository: LokcalImportRepository,
) {
    AppTheme {
        ConfigureCoilImageLoader()

        val database by produceState<Database?>(null) {
            value = createDatabase(sqlDriverFactory)
        }
        val db = database
        if (db == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@AppTheme
        }

        val shoppingListRepository = remember(db) { ShoppingListRepository(db) }
        val shoppingListViewModel = remember(shoppingListRepository) { ShoppingListViewModel(shoppingListRepository) }
        val lokcalSetupViewModel = remember(lokcalImportRepository, db) {
            LokcalSetupViewModel(lokcalImportRepository, db)
        }

        var screen by remember { mutableStateOf(Screen.ShoppingList) }
        var showAddItemSheet by remember { mutableStateOf(false) }
        var hasSnapshot by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            hasSnapshot = lokcalCatalogReader.hasSnapshot()
        }

        // Photos for the top collage — meal photos are preferred, topped up with food photos
        // only to fill the mosaic. Each is shuffled so the collage varies per launch, with meals
        // kept ahead of foods. Empty on platforms/states without a snapshot (the screen then
        // falls back to the list items' own photos, and to a plain header if there are none).
        val collageImageUrls by produceState(emptyList<String>(), lokcalCatalogReader, hasSnapshot) {
            value = runCatching {
                val meals = lokcalCatalogReader.browseMealImages(60).shuffled()
                val foods = lokcalCatalogReader.browseFoods(60).mapNotNull { it.imageUrl }.shuffled()
                (meals + foods).distinct()
            }.getOrDefault(emptyList())
        }

        when (screen) {
            Screen.ShoppingList -> ShoppingListScreen(
                viewModel = shoppingListViewModel,
                hasSnapshot = hasSnapshot,
                collageImageUrls = collageImageUrls,
                onOpenSetup = { screen = Screen.LokcalSetup },
                onAddItem = { showAddItemSheet = true },
            )

            Screen.LokcalSetup -> LokcalSetupScreen(
                viewModel = lokcalSetupViewModel,
                onBack = { screen = Screen.ShoppingList },
            )
        }

        if (showAddItemSheet) {
            AddItemSheet(
                catalogReader = lokcalCatalogReader,
                onDismiss = { showAddItemSheet = false },
                onFoodSelected = { food ->
                    shoppingListViewModel.addItem(food.id, food.name, food.imageUrl)
                },
            )
        }
    }
}
