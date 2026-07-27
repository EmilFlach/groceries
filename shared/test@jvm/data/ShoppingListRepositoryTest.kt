package com.emilflach.groceries.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emilflach.groceries.Database
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ShoppingListRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: Database
    private lateinit var repository: ShoppingListRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.synchronous().create(driver)
        database = Database(driver)
        repository = ShoppingListRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun testAddAndGetAll() = runTest {
        val result = repository.add(1L, "Milk", "https://example.com/milk.jpg")
        assertIs<AddItemResult.Added>(result)

        val all = repository.getAll()
        assertEquals(1, all.size)
        assertEquals("Milk", all[0].name)
        assertEquals(1L, all[0].lokcal_food_id)
        assertNull(all[0].checked_at)
    }

    @Test
    fun testAddDedupWhileActive() = runTest {
        repository.add(1L, "Milk", null)
        val second = repository.add(1L, "Milk", null)
        assertIs<AddItemResult.AlreadyOnList>(second)

        val all = repository.getAll()
        assertEquals(1, all.size)
    }

    @Test
    fun testCheckOff() = runTest {
        val added = repository.add(1L, "Milk", null) as AddItemResult.Added
        repository.setChecked(added.id, true)

        val all = repository.getAll()
        assertEquals(1, all.size)
        assertNotNull(all[0].checked_at)
    }

    @Test
    fun testUncheck() = runTest {
        val added = repository.add(1L, "Milk", null) as AddItemResult.Added
        repository.setChecked(added.id, true)
        repository.setChecked(added.id, false)

        val all = repository.getAll()
        assertNull(all[0].checked_at)
    }

    @Test
    fun testRemove() = runTest {
        val added = repository.add(1L, "Milk", null) as AddItemResult.Added
        repository.remove(added.id)

        assertEquals(0, repository.getAll().size)
    }

    @Test
    fun testReAddAfterChecked() = runTest {
        val first = repository.add(1L, "Milk", null) as AddItemResult.Added
        repository.setChecked(first.id, true)

        // Checked items don't block a new active item for the same food.
        val second = repository.add(1L, "Milk", null)
        assertIs<AddItemResult.Added>(second)

        val all = repository.getAll()
        assertEquals(2, all.size)
    }
}
