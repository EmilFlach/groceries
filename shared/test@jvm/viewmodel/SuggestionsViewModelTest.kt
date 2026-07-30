@file:OptIn(ExperimentalCoroutinesApi::class)

package com.emilflach.groceries.viewmodel

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emilflach.groceries.Database
import com.emilflach.groceries.data.DismissedSuggestionRepository
import com.emilflach.groceries.data.FoodLabelRepository
import com.emilflach.groceries.data.RegularItemRepository
import com.emilflach.groceries.data.ShoppingListRepository
import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.recommendations.RecommendationRepository
import com.emilflach.groceries.recommendations.RecommendationSource
import com.emilflach.groceries.recommendations.RegularMealSource
import com.emilflach.groceries.recommendations.Suggestion
import com.emilflach.groceries.recommendations.SuggestionGroup
import com.emilflach.groceries.recommendations.WeeklyRegularSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var regulars: RegularItemRepository
    private lateinit var dismissed: DismissedSuggestionRepository
    private lateinit var labels: FoodLabelRepository
    private lateinit var shoppingList: ShoppingListViewModel
    private lateinit var viewModel: SuggestionsViewModel

    private fun catalog(name: String) =
        Suggestion(normalizeKey(name), name, imageUrl = null, lokcalFoodId = name.hashCode().toLong())

    private val milk = catalog("Milk")
    private val eggs = catalog("Eggs")
    // A free-typed manual item (no catalog id), like something the user typed in themselves.
    private val napkins = Suggestion(normalizeKey("Napkins"), "Napkins", imageUrl = null, lokcalFoodId = null)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.synchronous().create(driver)
        val db = Database(driver)
        regulars = RegularItemRepository(db)
        dismissed = DismissedSuggestionRepository(db)
        labels = FoodLabelRepository(db)
        shoppingList = ShoppingListViewModel(ShoppingListRepository(db), labels)

        val source = object : RecommendationSource {
            override val id = "fake"
            override suspend fun load() =
                listOf(SuggestionGroup("fake", "Weekly regulars", listOf(milk, eggs, napkins), true))
        }
        viewModel = SuggestionsViewModel(RecommendationRepository(listOf(source)), regulars, dismissed, shoppingList)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        driver.close()
    }

    private fun activeKeys() =
        shoppingList.items.value.filter { it.checked_at == null }.map { normalizeKey(it.name) }

    @Test
    fun addedFlipsWhenListChanges() = runTest {
        viewModel.refresh(); advanceUntilIdle()
        assertTrue(viewModel.groups.value.single().items.none { it.added }, "nothing added initially")

        viewModel.toggle(milk); advanceUntilIdle()

        val milkUi = viewModel.groups.value.single().items.single { it.suggestion.key == "milk" }
        assertTrue(milkUi.added, "milk flips to added once on the list")
        assertTrue("milk" in activeKeys())
    }

    @Test
    fun toggleAddThenRemoveIsIdempotentForManual() = runTest {
        viewModel.refresh(); advanceUntilIdle()

        viewModel.toggle(napkins); advanceUntilIdle()
        assertEquals(1, activeKeys().count { it == "napkins" }, "added once")

        viewModel.toggle(napkins); advanceUntilIdle()
        assertEquals(0, activeKeys().count { it == "napkins" }, "removed again")
    }

    @Test
    fun addAllSkipsAlreadyAdded() = runTest {
        viewModel.refresh(); advanceUntilIdle()
        viewModel.toggle(milk); advanceUntilIdle()

        viewModel.addAll(viewModel.groups.value.single()); advanceUntilIdle()

        assertTrue(viewModel.groups.value.single().items.all { it.added }, "everything now added")
        assertEquals(1, activeKeys().count { it == "milk" }, "milk not duplicated")
    }

    @Test
    fun checkedItemStaysMarkedAsAdded() = runTest {
        viewModel.refresh(); advanceUntilIdle()
        viewModel.toggle(milk); advanceUntilIdle()
        val row = shoppingList.items.value.single { normalizeKey(it.name) == "milk" }

        shoppingList.setChecked(row.id, true); advanceUntilIdle()

        // In the cart still counts as on the list, so the suggestion stays marked as added rather
        // than reading as addable (which used to invite a duplicate).
        val milkUi = viewModel.groups.value.single().items.single { it.suggestion.key == "milk" }
        assertTrue(milkUi.added, "checked (in-cart) milk still shows as added")
        assertTrue(activeKeys().none { it == "milk" }, "and it's no longer active")
    }

    @Test
    fun togglingCheckedItemRemovesItWithoutDuplicating() = runTest {
        viewModel.refresh(); advanceUntilIdle()
        viewModel.toggle(milk); advanceUntilIdle()
        val row = shoppingList.items.value.single { normalizeKey(it.name) == "milk" }
        shoppingList.setChecked(row.id, true); advanceUntilIdle()

        // Previously this added a *second* milk row (checked + active), which crashed on uncheck.
        viewModel.toggle(milk); advanceUntilIdle()

        assertTrue(
            shoppingList.items.value.none { normalizeKey(it.name) == "milk" },
            "the in-cart milk is removed and no duplicate active row is created",
        )
    }

    @Test
    fun aisleNamesReflectAssignedLabels() = runTest {
        labels.ensureDefaultAisles()
        val aisle = labels.aisles().first()
        labels.setLabel("Milk", aisle.id)
        shoppingList.refresh(); advanceUntilIdle()

        // Keyed by normalized name so a suggestion card can look up its aisle by key.
        assertEquals(aisle.name, viewModel.aisleNames.value[normalizeKey("Milk")])
        assertFalse(normalizeKey("Eggs") in viewModel.aisleNames.value, "unlabeled foods have no aisle")
    }

    @Test
    fun markingRegularHidesItFromSuggestedLive() = runTest {
        // A source whose "Suggested" group carries milk and which — unlike the real one — does NOT
        // itself drop regulars. Proves the ViewModel keeps a freshly-marked regular out of Suggested
        // on its own, not relying on the source re-filtering.
        val source = object : RecommendationSource {
            override val id = "s"
            override suspend fun load() = listOf(
                SuggestionGroup(WeeklyRegularSource.SUGGESTED_ID, "Suggested", listOf(milk, eggs), supportsBulkAdd = true),
            )
        }
        val vm = SuggestionsViewModel(RecommendationRepository(listOf(source)), regulars, dismissed, shoppingList)
        vm.refresh(); advanceUntilIdle()
        assertEquals(setOf("milk", "eggs"), vm.groups.value.single().items.map { it.suggestion.key }.toSet())

        vm.markRegular(milk); advanceUntilIdle()

        assertEquals(
            listOf("eggs"),
            vm.groups.value.single { it.title == "Suggested" }.items.map { it.suggestion.key },
            "milk drops out of Suggested the moment it's marked regular",
        )
    }

    private fun suggestedSourceVm(vararg suggestions: Suggestion): SuggestionsViewModel {
        val source = object : RecommendationSource {
            override val id = "s"
            override suspend fun load() =
                listOf(SuggestionGroup(WeeklyRegularSource.SUGGESTED_ID, "Suggested", suggestions.toList(), true))
        }
        return SuggestionsViewModel(RecommendationRepository(listOf(source)), regulars, dismissed, shoppingList)
    }

    @Test
    fun dismissingSuggestionHidesItAndPersists() = runTest {
        val vm = suggestedSourceVm(milk, eggs)
        vm.refresh(); advanceUntilIdle()

        vm.dismiss(milk); advanceUntilIdle()
        assertEquals(listOf("eggs"), vm.groups.value.single().items.map { it.suggestion.key }, "milk hidden live")
        assertTrue(dismissed.all().foods.contains("milk"), "and the dismissal is persisted")

        vm.restore(milk); advanceUntilIdle()
        assertEquals(setOf("milk", "eggs"), vm.groups.value.single().items.map { it.suggestion.key }.toSet(), "undo brings it back")
    }

    @Test
    fun mealKeepsIngredientsThatAreRegulars() = runTest {
        // "pasta" is a manual regular and also an ingredient of a meal. It must stay hidden from
        // Suggested (already a regular) yet still appear in the meal — recipes show every ingredient.
        regulars.mark("Pasta", null, null)
        val pasta = catalog("Pasta")
        val sauce = catalog("Sauce")
        val source = object : RecommendationSource {
            override val id = "x"
            override suspend fun load() = listOf(
                SuggestionGroup(WeeklyRegularSource.SUGGESTED_ID, "Suggested", listOf(pasta, sauce), true),
                SuggestionGroup("${RegularMealSource.ID}:1", "Pasta dish", listOf(pasta, sauce), true),
            )
        }
        val vm = SuggestionsViewModel(RecommendationRepository(listOf(source)), regulars, dismissed, shoppingList)
        vm.refresh(); advanceUntilIdle()

        assertEquals(
            listOf("sauce"),
            vm.groups.value.single { it.title == "Suggested" }.items.map { it.suggestion.key },
            "the regular is hidden from Suggested",
        )
        assertEquals(
            listOf("pasta", "sauce"),
            vm.groups.value.single { it.title == "Pasta dish" }.items.map { it.suggestion.key },
            "but the meal keeps its full ingredient list",
        )
    }

    @Test
    fun dismissingMealHidesTheWholeGroup() = runTest {
        val ingredient = catalog("Spaghetti")
        val source = object : RecommendationSource {
            override val id = "m"
            override suspend fun load() = listOf(
                SuggestionGroup("${RegularMealSource.ID}:1", "Pasta", listOf(ingredient), supportsBulkAdd = true),
                SuggestionGroup(WeeklyRegularSource.SUGGESTED_ID, "Suggested", listOf(milk), supportsBulkAdd = true),
            )
        }
        val vm = SuggestionsViewModel(RecommendationRepository(listOf(source)), regulars, dismissed, shoppingList)
        vm.refresh(); advanceUntilIdle()
        val pasta = vm.groups.value.single { it.title == "Pasta" }

        vm.dismissMeal(pasta); advanceUntilIdle()
        assertEquals(listOf("Suggested"), vm.groups.value.map { it.title }, "the Pasta meal group is gone")
        assertTrue(dismissed.all().meals.contains("pasta"))

        vm.restoreMeal(pasta); advanceUntilIdle()
        assertEquals(setOf("Pasta", "Suggested"), vm.groups.value.map { it.title }.toSet(), "undo restores the meal")
    }

    @Test
    fun markRegularTogglesRowAndKeys() = runTest {
        viewModel.refresh(); advanceUntilIdle()

        viewModel.markRegular(milk); advanceUntilIdle()
        assertTrue(regulars.isRegular("Milk"))
        assertTrue("milk" in viewModel.regularKeys.value)

        viewModel.markRegular(milk); advanceUntilIdle()
        assertFalse(regulars.isRegular("Milk"))
        assertFalse("milk" in viewModel.regularKeys.value)
    }
}
