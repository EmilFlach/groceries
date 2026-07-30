package com.emilflach.groceries.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.emilflach.groceries.Database

/** The dismissed keys, split by kind — foods (normalized food names) and meals (normalized names). */
data class DismissedKeys(val foods: Set<String>, val meals: Set<String>)

/**
 * Persists the foods and meals the user dismissed ("Not interested") from auto recommendations.
 * Keyed by [normalizeKey] so a dismissal survives across shops and matches how suggestions are keyed.
 * Read by [com.emilflach.groceries.viewmodel.SuggestionsViewModel], which filters dismissed items out
 * of the "Suggested" group and meal groups (manual "Weekly regulars" are untouched).
 */
class DismissedSuggestionRepository(database: Database) {
    private val queries = database.dismissedSuggestionQueries

    suspend fun all(): DismissedKeys {
        val rows = queries.selectAll().awaitAsList()
        return DismissedKeys(
            foods = rows.asSequence().filter { it.kind == KIND_FOOD }.map { it.item_key }.toSet(),
            meals = rows.asSequence().filter { it.kind == KIND_MEAL }.map { it.item_key }.toSet(),
        )
    }

    suspend fun dismissFood(name: String) = queries.upsert(KIND_FOOD, normalizeKey(name))

    suspend fun dismissMeal(mealName: String) = queries.upsert(KIND_MEAL, normalizeKey(mealName))

    suspend fun restoreFood(name: String) = queries.deleteByKey(KIND_FOOD, normalizeKey(name))

    suspend fun restoreMeal(mealName: String) = queries.deleteByKey(KIND_MEAL, normalizeKey(mealName))

    private companion object {
        const val KIND_FOOD = "FOOD"
        const val KIND_MEAL = "MEAL"
    }
}
