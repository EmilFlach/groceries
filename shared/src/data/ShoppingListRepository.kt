package com.emilflach.groceries.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.emilflach.groceries.Database
import com.emilflach.groceries.ShoppingListItem
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed interface AddItemResult {
    data class Added(val id: Long) : AddItemResult
    data object AlreadyOnList : AddItemResult
}

class ShoppingListRepository(database: Database) {
    private val queries = database.shoppingListItemQueries

    suspend fun getAll(): List<ShoppingListItem> = queries.selectAll().awaitAsList()

    suspend fun add(lokcalFoodId: Long, name: String, imageUrl: String?, note: String? = null): AddItemResult {
        if (queries.selectActiveByFoodId(lokcalFoodId).awaitAsOneOrNull() != null) {
            return AddItemResult.AlreadyOnList
        }
        val id = queries.transactionWithResult {
            queries.insertItem(lokcalFoodId, name, imageUrl, note)
            queries.selectLastInsertRowId().awaitAsOne()
        }
        return AddItemResult.Added(id)
    }

    /**
     * Adds a free-typed item that isn't in the Lokcal catalog. It gets a unique negative synthetic
     * food id (one below the current minimum) so it never collides with real Lokcal ids (always
     * positive) and each custom item stays distinct under the active-food unique index — letting
     * several custom items coexist and skipping the "already on list" dedup entirely.
     */
    suspend fun addManual(name: String, note: String? = null): AddItemResult.Added {
        val id = queries.transactionWithResult {
            val minFoodId = queries.selectMinFoodId { min -> min ?: 0L }.awaitAsOne()
            val syntheticFoodId = minOf(0L, minFoodId) - 1
            queries.insertItem(syntheticFoodId, name, null, note)
            queries.selectLastInsertRowId().awaitAsOne()
        }
        return AddItemResult.Added(id)
    }

    /** Sets (or clears, with a null/blank value) the free-text note on an item. */
    suspend fun updateNote(id: Long, note: String?) {
        queries.updateNote(note?.trim()?.ifBlank { null }, id)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun setChecked(id: Long, checked: Boolean) {
        if (checked) {
            queries.setChecked(Clock.System.now().toString(), id)
            return
        }
        // Reviving a checked row must not produce a second *active* row for the same food: the
        // partial unique index (active rows only) forbids it and unchecking would otherwise crash
        // with a constraint violation. If that food is already active, this checked row is redundant
        // — drop it instead of unchecking. Mirrors uncheckAll's guard for the single-row case.
        val row = queries.selectById(id).awaitAsOneOrNull() ?: return
        if (queries.selectActiveByFoodId(row.lokcal_food_id).awaitAsOneOrNull() != null) {
            queries.deleteById(id)
        } else {
            queries.setChecked(null, id)
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun checkAll() {
        queries.checkAll(Clock.System.now().toString())
    }

    suspend fun uncheckAll() {
        queries.uncheckAll()
    }

    suspend fun remove(id: Long) {
        queries.deleteById(id)
    }

    /** Permanently removes every checked ("in the cart") item — for starting a fresh weekly list. */
    suspend fun clearChecked() {
        queries.deleteChecked()
    }
}
