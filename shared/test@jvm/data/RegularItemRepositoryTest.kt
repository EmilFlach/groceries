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

class RegularItemRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: RegularItemRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.synchronous().create(driver)
        repository = RegularItemRepository(Database(driver))
    }

    @AfterTest
    fun teardown() = driver.close()

    @Test
    fun schemaIsAtLatestVersion() {
        // Bumps as migrations are added: v1 base, +1.sqm (aisles), +2.sqm (RegularItem),
        // +3.sqm (DismissedSuggestion), +4.sqm (ShoppingListItem.note) = 5. Guards that new .sq
        // columns/tables are wired with a migration.
        assertEquals(5L, Database.Schema.version)
    }

    @Test
    fun markUnmarkRoundTrip() = runTest {
        repository.mark("Milk", "https://img/milk.jpg", 7L)

        assertTrue(repository.isRegular("Milk"))
        val all = repository.all()
        assertEquals(1, all.size)
        assertEquals("Milk", all[0].name)
        assertEquals("https://img/milk.jpg", all[0].image_url)
        assertEquals(7L, all[0].lokcal_food_id)

        repository.unmark("Milk")
        assertFalse(repository.isRegular("Milk"))
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun keyedByNormalizedName() = runTest {
        repository.mark("  Whole  Milk ", null, null)

        // Differently-cased/spaced variants resolve to the same normalized key.
        assertTrue(repository.isRegular("whole milk"))
        assertEquals("whole milk", repository.all().single().food_key)

        // A second mark of an equivalent name upserts rather than duplicating.
        repository.mark("WHOLE MILK", null, null)
        assertEquals(1, repository.all().size)
    }
}
