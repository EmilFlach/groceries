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
    fun testCheckAllChecksEverything() = runTest {
        repository.add(1L, "Milk", null)
        repository.add(2L, "Eggs", null)

        repository.checkAll()

        val all = repository.getAll()
        assertEquals(2, all.size)
        assertTrue(all.all { it.checked_at != null })
    }

    @Test
    fun testCheckAllLeavesAlreadyCheckedItems() = runTest {
        val milk = repository.add(1L, "Milk", null) as AddItemResult.Added
        repository.add(2L, "Eggs", null)
        repository.setChecked(milk.id, true)

        repository.checkAll()

        // Everything ends up checked, and the pre-checked item isn't disturbed.
        val all = repository.getAll()
        assertEquals(2, all.size)
        assertTrue(all.all { it.checked_at != null })
    }

    @Test
    fun testUncheckAllRevivesEverything() = runTest {
        val milk = repository.add(1L, "Milk", null) as AddItemResult.Added
        val eggs = repository.add(2L, "Eggs", null) as AddItemResult.Added
        repository.setChecked(milk.id, true)
        repository.setChecked(eggs.id, true)

        repository.uncheckAll()

        val all = repository.getAll()
        assertEquals(2, all.size)
        assertTrue(all.all { it.checked_at == null })
    }

    @Test
    fun testUncheckAllSkipsFoodAlreadyActive() = runTest {
        // Same food both checked and re-added as active — reviving the checked row would
        // violate the active-food unique index, so uncheckAll must leave it checked.
        val first = repository.add(1L, "Milk", null) as AddItemResult.Added
        repository.setChecked(first.id, true)
        repository.add(1L, "Milk", null) as AddItemResult.Added

        repository.uncheckAll()

        val all = repository.getAll()
        assertEquals(2, all.size)
        assertEquals(1, all.count { it.checked_at == null })
        assertEquals(1, all.count { it.checked_at != null })
    }

    @Test
    fun testAddManualItem() = runTest {
        val result = repository.addManual("Napkins")

        val all = repository.getAll()
        assertEquals(1, all.size)
        assertEquals("Napkins", all[0].name)
        assertEquals(result.id, all[0].id)
        assertNull(all[0].image_url)
        assertNull(all[0].checked_at)
        // Synthetic food id is negative so it never collides with real (positive) Lokcal ids.
        assertTrue(all[0].lokcal_food_id < 0)
    }

    @Test
    fun testMultipleManualItemsCoexist() = runTest {
        // Each manual add gets its own negative id, so several stay active at once without the
        // active-food unique index treating them as duplicates.
        repository.addManual("Napkins")
        repository.addManual("Batteries")
        repository.addManual("Napkins")

        val all = repository.getAll()
        assertEquals(3, all.size)
        assertEquals(3, all.map { it.lokcal_food_id }.distinct().size)
        assertTrue(all.all { it.lokcal_food_id < 0 })
    }

    @Test
    fun testManualItemDoesNotClashWithCatalogFood() = runTest {
        repository.add(1L, "Milk", null)
        repository.addManual("Napkins")

        val all = repository.getAll()
        assertEquals(2, all.size)
        assertEquals(setOf(1L), all.filter { it.lokcal_food_id > 0 }.map { it.lokcal_food_id }.toSet())
        assertEquals(1, all.count { it.lokcal_food_id < 0 })
    }

    @Test
    fun testClearCheckedRemovesOnlyCheckedItems() = runTest {
        val milk = repository.add(1L, "Milk", null) as AddItemResult.Added
        repository.add(2L, "Eggs", null)
        repository.setChecked(milk.id, true)

        repository.clearChecked()

        // Only the checked "Milk" is gone; the active "Eggs" remains.
        val all = repository.getAll()
        assertEquals(1, all.size)
        assertEquals("Eggs", all[0].name)
        assertNull(all[0].checked_at)
    }

    @Test
    fun testClearCheckedNoOpWhenNothingChecked() = runTest {
        repository.add(1L, "Milk", null)
        repository.clearChecked()
        assertEquals(1, repository.getAll().size)
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

    @Test
    fun testUncheckDropsRedundantRowWhenFoodAlreadyActive() = runTest {
        // Reproduces the crash: a food that's both checked and active (e.g. re-added from the "Add"
        // screen while still in the cart). Unchecking the checked row must not create a second
        // active row — the active-food unique index would reject it — so the redundant row is
        // dropped instead. Previously this threw a constraint violation and crashed the app.
        val checked = repository.add(1L, "Milk", null) as AddItemResult.Added
        repository.setChecked(checked.id, true)
        val active = repository.add(1L, "Milk", null) as AddItemResult.Added

        repository.setChecked(checked.id, false)

        val all = repository.getAll()
        assertEquals(1, all.size, "the redundant checked row is dropped, not revived into a duplicate")
        assertEquals(active.id, all[0].id)
        assertNull(all[0].checked_at)
    }
}
