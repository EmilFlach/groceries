package com.emilflach.groceries.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.emilflach.groceries.Aisle
import com.emilflach.groceries.Database

/** Default supermarket aisles, seeded once. `id` is stable (used by FoodLabel.aisle_id); the
 *  gaps of 10 in sort_order leave room to slot new aisles between existing ones later. Fruit &
 *  Vegetables is first (0) so it sorts to the top of the "to buy" list. */
private val DEFAULT_AISLES = listOf(
    Triple(1L, "Fruit & Vegetables", 0L),
    Triple(2L, "Bakery", 10L),
    Triple(3L, "Dairy & Eggs", 20L),
    Triple(4L, "Meat & Fish", 30L),
    Triple(5L, "Pasta & Rice", 40L),
    Triple(6L, "Cans & Jars", 50L),
    Triple(7L, "Frozen", 60L),
    Triple(8L, "Drinks", 70L),
    Triple(9L, "Snacks", 80L),
    Triple(10L, "Household", 90L),
)

class FoodLabelRepository(database: Database) {
    private val aisleQueries = database.aisleQueries
    private val labelQueries = database.foodLabelQueries

    /** Inserts the default aisles if they aren't there yet. Idempotent (OR IGNORE), so it's safe to
     *  call on every startup and it won't clobber user renames/reorders. */
    suspend fun ensureDefaultAisles() {
        for ((id, name, order) in DEFAULT_AISLES) {
            aisleQueries.insertDefaultAisle(id, name, order)
        }
    }

    suspend fun aisles(): List<Aisle> = aisleQueries.selectAllOrdered().awaitAsList()

    /** Adds a new aisle at the end of the walk order. Blank names are ignored. */
    suspend fun addAisle(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val maxOrder = aisleQueries.selectMaxSortOrder { it ?: -10L }.awaitAsOne()
        aisleQueries.insertAisle(trimmed, maxOrder + 10L)
    }

    suspend fun renameAisle(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        aisleQueries.updateName(trimmed, id)
    }

    /** Removes an aisle and drops the labels that pointed at it (those foods fall back to "Other"). */
    suspend fun deleteAisle(id: Long) {
        aisleQueries.transaction {
            labelQueries.deleteByAisle(id)
            aisleQueries.deleteAisle(id)
        }
    }

    /** Persists a new walk order by rewriting every aisle's sort_order to its position (×10, so
     *  there's room to insert between later). Pass the aisle ids in the desired order. */
    suspend fun reorderAisles(orderedIds: List<Long>) {
        aisleQueries.transaction {
            orderedIds.forEachIndexed { index, id ->
                aisleQueries.updateSortOrder(index * 10L, id)
            }
        }
    }

    /** All assignments as a `normalizedName -> aisleId` map for in-memory grouping of the list. */
    suspend fun labels(): Map<String, Long> =
        labelQueries.selectAll().awaitAsList().associate { it.food_key to it.aisle_id }

    suspend fun setLabel(name: String, aisleId: Long) {
        labelQueries.upsert(normalizeKey(name), aisleId)
    }

    suspend fun clearLabel(name: String) {
        labelQueries.deleteByKey(normalizeKey(name))
    }
}

/** Stable identity for a food across catalog picks and manual entries: lowercased, trimmed, with
 *  internal whitespace runs collapsed to a single space. */
fun normalizeKey(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), " ")
