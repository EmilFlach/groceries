@file:OptIn(ExperimentalCoroutinesApi::class)

package com.emilflach.groceries.viewmodel

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emilflach.groceries.Database
import com.emilflach.groceries.data.FoodLabelRepository
import com.emilflach.groceries.data.RegularItemRepository
import com.emilflach.groceries.data.ShoppingListRepository
import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.recommendations.RecommendationRepository
import com.emilflach.groceries.recommendations.RecommendationSource
import com.emilflach.groceries.recommendations.Suggestion
import com.emilflach.groceries.recommendations.SuggestionGroup
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
    private lateinit var shoppingList: ShoppingListViewModel
    private lateinit var viewModel: SuggestionsViewModel

    private fun catalog(name: String) =
        Suggestion(normalizeKey(name), name, imageUrl = null, lokcalFoodId = name.hashCode().toLong())
    private fun manual(name: String) =
        Suggestion(normalizeKey(name), name, imageUrl = null, lokcalFoodId = null)

    private val milk = catalog("Milk")
    private val eggs = catalog("Eggs")
    private val napkins = manual("Napkins")

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.synchronous().create(driver)
        val db = Database(driver)
        regulars = RegularItemRepository(db)
        shoppingList = ShoppingListViewModel(ShoppingListRepository(db), FoodLabelRepository(db))

        val source = object : RecommendationSource {
            override val id = "fake"
            override suspend fun load() =
                listOf(SuggestionGroup("fake", "Weekly regulars", listOf(milk, eggs, napkins), true))
        }
        viewModel = SuggestionsViewModel(RecommendationRepository(listOf(source)), regulars, shoppingList)
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
