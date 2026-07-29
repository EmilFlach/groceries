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

    suspend fun add(lokcalFoodId: Long, name: String, imageUrl: String?): AddItemResult {
        if (queries.selectActiveByFoodId(lokcalFoodId).awaitAsOneOrNull() != null) {
            return AddItemResult.AlreadyOnList
        }
        val id = queries.transactionWithResult {
            queries.insertItem(lokcalFoodId, name, imageUrl)
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
    suspend fun addManual(name: String): AddItemResult.Added {
        val id = queries.transactionWithResult {
            val minFoodId = queries.selectMinFoodId { min -> min ?: 0L }.awaitAsOne()
            val syntheticFoodId = minOf(0L, minFoodId) - 1
            queries.insertItem(syntheticFoodId, name, null)
            queries.selectLastInsertRowId().awaitAsOne()
        }
        return AddItemResult.Added(id)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun setChecked(id: Long, checked: Boolean) {
        queries.setChecked(if (checked) Clock.System.now().toString() else null, id)
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
