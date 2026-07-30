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
    fun splitsManualAndAutoIntoSeparateGroups() = runTest {
        regulars.mark("Napkins", null, null)
        val source = WeeklyRegularSource(frequent("Milk", "Eggs"), regulars)

        val groups = source.load()
        assertEquals(listOf("Weekly regulars", "Suggested"), groups.map { it.title })
        assertTrue(groups.all { it.supportsBulkAdd })
        assertEquals(setOf("napkins"), groups[0].suggestions.map { it.key }.toSet())
        assertEquals(setOf("milk", "eggs"), groups[1].suggestions.map { it.key }.toSet())
    }

    @Test
    fun autoFoodAlreadyMarkedManualIsNotSuggestedAgain() = runTest {
        regulars.mark("Milk", "https://user/milk.jpg", 42L)
        val source = WeeklyRegularSource(frequent("Milk", "Eggs"), regulars)

        val groups = source.load()
        // Milk stays only under the manual group, carrying the user's own image/id.
        val milk = groups.single { it.title == "Weekly regulars" }.suggestions.single { it.key == "milk" }
        assertEquals("https://user/milk.jpg", milk.imageUrl)
        assertEquals(42L, milk.lokcalFoodId)
        // ...and isn't repeated under Suggested, which keeps only the non-regular auto foods.
        assertEquals(listOf("eggs"), groups.single { it.title == "Suggested" }.suggestions.map { it.key })
    }

    @Test
    fun onlyManualShowsSingleWeeklyRegularsGroup() = runTest {
        regulars.mark("Napkins", null, null)
        val source = WeeklyRegularSource(frequent(), regulars)

        assertEquals(listOf("Weekly regulars"), source.load().map { it.title })
    }

    @Test
    fun emptyWhenNoManualAndNoAuto() = runTest {
        val source = WeeklyRegularSource(frequent(), regulars)
        assertTrue(source.load().isEmpty())
    }
}
