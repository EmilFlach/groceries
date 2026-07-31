package com.emilflach.groceries.viewmodel

import com.emilflach.groceries.Aisle
import com.emilflach.groceries.ShoppingListItem
import com.emilflach.groceries.data.normalizeKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupByAisleTest {
    private val aisles = listOf(
        Aisle(1L, "Fruit & Vegetables", 0L),
        Aisle(5L, "Pasta & Rice", 40L),
    )

    private fun item(id: Long, name: String, foodId: Long = id): ShoppingListItem =
        ShoppingListItem(
            id = id,
            lokcal_food_id = foodId,
            name = name,
            image_url = null,
            note = null,
            added_at = "2026-07-28T00:00:0$id",
            checked_at = null,
        )

    @Test
    fun testGroupsOrderedByAisleWithUnlabeledLast() {
        val items = listOf(
            item(1, "Spaghetti"),
            item(2, "Apple"),
            item(3, "Mystery"),
        )
        val labels = mapOf(
            normalizeKey("Spaghetti") to 5L,
            normalizeKey("Apple") to 1L,
        )

        val groups = groupByAisle(items, labels, aisles)

        assertEquals(listOf("Fruit & Vegetables", "Pasta & Rice", "Other"), groups.map { it.title })
        assertEquals(listOf("Apple"), groups[0].items.map { it.name })
        assertEquals(listOf("Spaghetti"), groups[1].items.map { it.name })
        assertNull(groups.last().aisleId)
        assertEquals(listOf("Mystery"), groups.last().items.map { it.name })
    }

    @Test
    fun testManualItemLabeledByName() {
        // A manual item carries a negative synthetic food id but is still matched to its aisle by name.
        val manual = item(9, "Bananas", foodId = -3L)
        val labels = mapOf(normalizeKey("bananas") to 1L)

        val groups = groupByAisle(listOf(manual), labels, aisles)

        assertEquals(1, groups.size)
        assertEquals(1L, groups[0].aisleId)
        assertEquals(listOf("Bananas"), groups[0].items.map { it.name })
    }

    @Test
    fun testNoLabelsProducesSingleOtherGroup() {
        val groups = groupByAisle(listOf(item(1, "Whatever")), emptyMap(), aisles)

        assertEquals(1, groups.size)
        assertNull(groups[0].aisleId)
        assertEquals("Other", groups[0].title)
    }

    @Test
    fun testLabelPointingAtMissingAisleFallsToOther() {
        val labels = mapOf(normalizeKey("Ghost") to 999L)

        val groups = groupByAisle(listOf(item(1, "Ghost")), labels, aisles)

        assertEquals(1, groups.size)
        assertNull(groups[0].aisleId)
    }
}
