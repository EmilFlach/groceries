package com.emilflach.groceries.lokcal

/**
 * Mirrors the subset of Lokcal's own `Food` table read from the imported snapshot.
 * Source of truth: Lokcal/shared/sqldelight/com/emilflach/lokcal/Food.sq
 */
data class LokcalFood(
    val id: Long,
    val name: String,
    val energyKcalPer100g: Double,
    val gtin13: String?,
    val imageUrl: String?,
    val productUrl: String?,
    val source: String?,
)

/**
 * A [LokcalFood] the user consumed across several recent weeks in Lokcal's Intake log —
 * the raw signal behind "weekly regulars" recommendations.
 *
 * @param distinctWeeks how many distinct calendar weeks (within the query window) it appeared in
 * @param lastEaten the most recent Intake timestamp for it (raw text, newest first ordering only)
 */
data class LokcalFrequentFood(
    val food: LokcalFood,
    val distinctWeeks: Int,
    val lastEaten: String?,
)

/**
 * Mirrors the subset of Lokcal's own `Meal` table read from the imported snapshot.
 * Source of truth: Lokcal/shared/sqldelight/com/emilflach/lokcal/Meals.sq
 */
data class LokcalMeal(
    val id: Long,
    val name: String,
    val imageUrl: String?,
)

/**
 * A [LokcalMeal] the user cooked across several recent weeks in Lokcal's Intake log — the raw signal
 * behind "meals you cook regularly" recommendations (each surfaces as its ingredient shopping list).
 *
 * @param distinctWeeks how many distinct calendar weeks (within the query window) it was logged in
 * @param lastEaten the most recent Intake timestamp for it (raw text, newest first ordering only)
 */
data class LokcalFrequentMeal(
    val meal: LokcalMeal,
    val distinctWeeks: Int,
    val lastEaten: String?,
)
