package com.emilflach.groceries.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emilflach.groceries.Database
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FoodLabelRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: Database
    private lateinit var repository: FoodLabelRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.synchronous().create(driver)
        database = Database(driver)
        repository = FoodLabelRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun testSeedDefaultAislesIsIdempotent() = runTest {
        repository.ensureDefaultAisles()
        val first = repository.aisles()
        assertEquals(10, first.size)
        // Fruit & Vegetables sorts first so it lands at the top of the list.
        assertEquals("Fruit & Vegetables", first.first().name)
        assertTrue(first.zipWithNext().all { (a, b) -> a.sort_order <= b.sort_order })

        // Running the seed again doesn't duplicate rows.
        repository.ensureDefaultAisles()
        assertEquals(10, repository.aisles().size)
    }

    @Test
    fun testSetLabelStoredUnderNormalizedKey() = runTest {
        repository.ensureDefaultAisles()
        repository.setLabel("  Bananas  ", 1L)

        val labels = repository.labels()
        assertEquals(mapOf("bananas" to 1L), labels)
        // Case/whitespace variants resolve to the same key.
        assertEquals(1L, labels[normalizeKey("BANANAS")])
        assertEquals("olive oil", normalizeKey("  Olive   Oil "))
    }

    @Test
    fun testSetLabelUpsertsExistingKey() = runTest {
        repository.ensureDefaultAisles()
        repository.setLabel("Pasta", 5L)
        repository.setLabel("pasta", 6L)

        assertEquals(mapOf("pasta" to 6L), repository.labels())
    }

    @Test
    fun testClearLabel() = runTest {
        repository.ensureDefaultAisles()
        repository.setLabel("Milk", 3L)
        repository.clearLabel("MILK")

        assertTrue(repository.labels().isEmpty())
    }

    @Test
    fun testAddAisleAppendsAfterLast() = runTest {
        repository.ensureDefaultAisles()
        val lastOrder = repository.aisles().maxOf { it.sort_order }

        repository.addAisle("  Deli  ")

        val aisles = repository.aisles()
        assertEquals(11, aisles.size)
        assertEquals("Deli", aisles.last().name)
        assertEquals(lastOrder + 10L, aisles.last().sort_order)
    }

    @Test
    fun testAddAisleIgnoresBlank() = runTest {
        repository.ensureDefaultAisles()
        repository.addAisle("   ")
        assertEquals(10, repository.aisles().size)
    }

    @Test
    fun testRenameAisle() = runTest {
        repository.ensureDefaultAisles()
        repository.renameAisle(1L, "Produce")
        assertEquals("Produce", repository.aisles().first { it.id == 1L }.name)
    }

    @Test
    fun testDeleteAisleAlsoRemovesItsLabels() = runTest {
        repository.ensureDefaultAisles()
        repository.setLabel("Spaghetti", 5L)
        repository.setLabel("Apple", 1L)

        repository.deleteAisle(5L)

        assertEquals(9, repository.aisles().size)
        assertNull(repository.aisles().firstOrNull { it.id == 5L })
        // The label pointing at the deleted aisle is gone; the other survives.
        assertEquals(mapOf("apple" to 1L), repository.labels())
    }

    @Test
    fun testReorderAislesRewritesSortOrderByPosition() = runTest {
        repository.ensureDefaultAisles()
        val ids = repository.aisles().map { it.id }
        // Move the last aisle to the front.
        val reordered = listOf(ids.last()) + ids.dropLast(1)

        repository.reorderAisles(reordered)

        val after = repository.aisles()
        assertEquals(reordered, after.map { it.id })
        assertEquals(0L, after.first().sort_order)
        assertEquals(listOf(0L, 10L, 20L), after.take(3).map { it.sort_order })
    }
}
