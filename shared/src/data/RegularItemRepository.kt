package com.emilflach.groceries.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.emilflach.groceries.Database
import com.emilflach.groceries.RegularItem

/**
 * Stores the foods the user has manually marked as a weekly/regular buy. Keyed by
 * [normalizeKey] so a mark covers both Lokcal catalog picks and free-typed items and survives
 * across shops. Read back by [com.emilflach.groceries.recommendations.WeeklyRegularSource].
 */
class RegularItemRepository(database: Database) {
    private val queries = database.regularItemQueries

    suspend fun all(): List<RegularItem> = queries.selectAll().awaitAsList()

    suspend fun isRegular(name: String): Boolean =
        queries.selectByKey(normalizeKey(name)).awaitAsOneOrNull() != null

    suspend fun mark(name: String, imageUrl: String?, lokcalFoodId: Long?) {
        queries.upsert(normalizeKey(name), name, imageUrl, lokcalFoodId)
    }

    suspend fun unmark(name: String) {
        queries.deleteByKey(normalizeKey(name))
    }
}
