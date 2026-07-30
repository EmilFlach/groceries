package com.emilflach.groceries.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emilflach.groceries.Database
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DismissedSuggestionRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: DismissedSuggestionRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.synchronous().create(driver)
        repository = DismissedSuggestionRepository(Database(driver))
    }

    @AfterTest
    fun teardown() = driver.close()

    @Test
    fun dismissesFoodsAndMealsInSeparateNamespaces() = runTest {
        repository.dismissFood("Whole Milk")
        repository.dismissMeal("Pasta Bolognese")

        val keys = repository.all()
        // Keyed by normalizeKey; foods and meals don't bleed into each other's set.
        assertEquals(setOf("whole milk"), keys.foods)
        assertEquals(setOf("pasta bolognese"), keys.meals)
    }

    @Test
    fun sameNameFoodAndMealCoexist() = runTest {
        repository.dismissFood("Soup")
        repository.dismissMeal("Soup")

        val keys = repository.all()
        assertTrue("soup" in keys.foods)
        assertTrue("soup" in keys.meals)
    }

    @Test
    fun restoreRemovesOnlyThatKind() = runTest {
        repository.dismissFood("Soup")
        repository.dismissMeal("Soup")

        repository.restoreFood("Soup")

        val keys = repository.all()
        assertFalse("soup" in keys.foods, "the food dismissal is lifted")
        assertTrue("soup" in keys.meals, "the meal dismissal is untouched")
    }

    @Test
    fun dismissIsIdempotent() = runTest {
        repository.dismissFood("Milk")
        repository.dismissFood("milk") // same normalized key

        assertEquals(setOf("milk"), repository.all().foods)
    }
}
