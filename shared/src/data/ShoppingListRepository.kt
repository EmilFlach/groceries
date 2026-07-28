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
}
