package com.emilflach.groceries.recommendations

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emilflach.groceries.Database
import com.emilflach.groceries.data.RegularItemRepository
import com.emilflach.groceries.lokcal.LokcalFood
import com.emilflach.groceries.lokcal.LokcalFrequentFood
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyRegularSourceTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var regulars: RegularItemRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.synchronous().create(driver)
        regulars = RegularItemRepository(Database(driver))
    }

    @AfterTest
    fun teardown() = driver.close()

    private fun frequent(vararg names: String) = FrequentFoodProvider { _, _, _ ->
        names.mapIndexed { i, name ->
            LokcalFrequentFood(
                food = LokcalFood(i.toLong(), name, 0.0, null, null, null, null),
                distinctWeeks = 5,
                lastEaten = null,
            )
        }
    }

    @Test
    fun mergesManualAndAutoIntoOneGroup() = runTest {
        regulars.mark("Napkins", null, null)
        val source = WeeklyRegularSource(frequent("Milk", "Eggs"), regulars)

        val group = source.load().single()
        assertEquals("Weekly regulars", group.title)
        assertTrue(group.supportsBulkAdd)
        assertEquals(setOf("napkins", "milk", "eggs"), group.suggestions.map { it.key }.toSet())
    }

    @Test
    fun manualWinsOnKeyCollision() = runTest {
        regulars.mark("Milk", "https://user/milk.jpg", 42L)
        val source = WeeklyRegularSource(frequent("Milk"), regulars)

        val milk = source.load().single().suggestions.single { it.key == "milk" }
        // The manual pin's image/id/reason survive, not the auto one.
        assertEquals("https://user/milk.jpg", milk.imageUrl)
        assertEquals(42L, milk.lokcalFoodId)
        assertEquals("Marked as regular", milk.reason)
    }

    @Test
    fun emptyWhenNoManualAndNoAuto() = runTest {
        val source = WeeklyRegularSource(frequent(), regulars)
        assertTrue(source.load().isEmpty())
    }
}
