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
import com.emilflach.groceries.data.DismissedSuggestionRepository
import com.emilflach.groceries.data.FoodLabelRepository
import com.emilflach.groceries.data.RegularItemRepository
import com.emilflach.groceries.data.ShoppingListRepository
import com.emilflach.groceries.data.SqlDriverFactory
import com.emilflach.groceries.data.createDatabase
import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalImportRepository
import com.emilflach.groceries.lokcal.LokcalFrequentMeal
import com.emilflach.groceries.lokcal.LokcalMealItem
import com.emilflach.groceries.recommendations.FrequentMealProvider
import com.emilflach.groceries.recommendations.RecommendationRepository
import com.emilflach.groceries.recommendations.RegularMealSource
import com.emilflach.groceries.recommendations.Suggestion
import com.emilflach.groceries.recommendations.WeeklyRegularSource
import com.emilflach.groceries.ui.screens.AddHubScreen
import com.emilflach.groceries.ui.screens.AisleSettingsScreen
import com.emilflach.groceries.ui.screens.LokcalSetupScreen
import com.emilflach.groceries.ui.screens.ShoppingListScreen
import com.emilflach.groceries.ui.theme.AppTheme
import com.emilflach.groceries.ui.util.ConfigureCoilImageLoader
import com.emilflach.groceries.viewmodel.AisleSettingsViewModel
import com.emilflach.groceries.viewmodel.LokcalSetupViewModel
import com.emilflach.groceries.viewmodel.ShoppingListViewModel
import com.emilflach.groceries.viewmodel.SuggestionsViewModel
import androidx.compose.runtime.collectAsState

private enum class Screen { ShoppingList, LokcalSetup, AisleSettings }

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
        val foodLabelRepository = remember(db) { FoodLabelRepository(db) }
        val regularItemRepository = remember(db) { RegularItemRepository(db) }
        val dismissedSuggestionRepository = remember(db) { DismissedSuggestionRepository(db) }
        val shoppingListViewModel = remember(shoppingListRepository, foodLabelRepository) {
            ShoppingListViewModel(shoppingListRepository, foodLabelRepository)
        }
        val suggestionsViewModel = remember(db, lokcalCatalogReader, shoppingListViewModel) {
            // Adapter over the reader's two meal calls (a fun-interface method reference can't carry
            // both), so [RegularMealSource] stays reader-agnostic and unit-testable with a fake.
            val mealProvider = object : FrequentMealProvider {
                override suspend fun frequentMeals(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentMeal> =
                    lokcalCatalogReader.frequentMeals(windowDays, minWeeks, limit)

                override suspend fun mealItems(mealId: Long): List<LokcalMealItem> =
                    lokcalCatalogReader.mealItems(mealId)
            }
            val recommendations = RecommendationRepository(
                // Regulars first so a food that's both a regular and a meal ingredient stays
                // under regulars (see RecommendationRepository's earlier-source-wins dedup).
                sources = listOf(
                    WeeklyRegularSource(lokcalCatalogReader::frequentFoods, regularItemRepository),
                    RegularMealSource(mealProvider),
                ),
            )
            SuggestionsViewModel(recommendations, regularItemRepository, dismissedSuggestionRepository, shoppingListViewModel)
        }

        // Seed the default supermarket aisles once, then reload so grouping picks them up.
        LaunchedEffect(foodLabelRepository) {
            foodLabelRepository.ensureDefaultAisles()
            shoppingListViewModel.refresh()
        }
        val lokcalSetupViewModel = remember(lokcalImportRepository, db) {
            LokcalSetupViewModel(lokcalImportRepository, db)
        }
        val aisleSettingsViewModel = remember(foodLabelRepository) { AisleSettingsViewModel(foodLabelRepository) }

        var screen by remember { mutableStateOf(Screen.ShoppingList) }
        var showAddItem by remember { mutableStateOf(false) }
        var hasSnapshot by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            hasSnapshot = lokcalCatalogReader.hasSnapshot()
        }

        // Top-collage photos: meals first, topped up with food photos, shuffled per launch.
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
                onAddItem = {
                    suggestionsViewModel.refresh()
                    showAddItem = true
                },
            )

            Screen.LokcalSetup -> LokcalSetupScreen(
                viewModel = lokcalSetupViewModel,
                onBack = {
                    screen = Screen.ShoppingList
                    // Pick up any aisle order/name changes made in settings.
                    shoppingListViewModel.refresh()
                },
                onManageAisles = {
                    aisleSettingsViewModel.refresh()
                    screen = Screen.AisleSettings
                },
            )

            Screen.AisleSettings -> AisleSettingsScreen(
                viewModel = aisleSettingsViewModel,
                onBack = { screen = Screen.LokcalSetup },
            )
        }

        if (showAddItem) {
            val groups by suggestionsViewModel.groups.collectAsState()
            val regularKeys by suggestionsViewModel.regularKeys.collectAsState()
            val addedKeys by suggestionsViewModel.onListKeys.collectAsState()
            val aisleNames by suggestionsViewModel.aisleNames.collectAsState()
            val aisles by shoppingListViewModel.aisles.collectAsState()
            val aisleIdByKey by shoppingListViewModel.labels.collectAsState()
            AddHubScreen(
                catalogReader = lokcalCatalogReader,
                groups = groups,
                regularKeys = regularKeys,
                addedKeys = addedKeys,
                aisleNames = aisleNames,
                aisles = aisles,
                aisleIdByKey = aisleIdByKey,
                onDismiss = { showAddItem = false },
                // Route through the shared toggle (not a plain add) so tapping an already-listed
                // food removes it instead of colliding on the active-food unique index — same
                // behaviour as the suggestion cards.
                onFoodSelected = { food ->
                    suggestionsViewModel.toggle(
                        Suggestion(
                            key = normalizeKey(food.name),
                            name = food.name,
                            imageUrl = food.imageUrl,
                            lokcalFoodId = food.id,
                        )
                    )
                },
                onAddCustom = { name ->
                    shoppingListViewModel.addManualItem(name)
                },
                onToggleSuggestion = { suggestionsViewModel.toggle(it) },
                onAddAll = { suggestionsViewModel.addAll(it) },
                onToggleRegular = { suggestionsViewModel.markRegular(it) },
                onAssignAisle = { suggestion, aisleId -> shoppingListViewModel.setLabel(suggestion.name, aisleId) },
                onClearAisle = { suggestion -> shoppingListViewModel.clearLabel(suggestion.name) },
                onDismissMeal = { suggestionsViewModel.dismissMeal(it) },
                onRestoreMeal = { suggestionsViewModel.restoreMeal(it) },
                onDismissSuggestion = { suggestionsViewModel.dismiss(it) },
                onRestoreSuggestion = { suggestionsViewModel.restore(it) },
            )
        }
    }
}
