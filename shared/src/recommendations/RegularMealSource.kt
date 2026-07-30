package com.emilflach.groceries.recommendations

import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalFood
import com.emilflach.groceries.lokcal.LokcalFrequentMeal

/**
 * Supplies the meals cooked across many recent weeks and their ingredient foods — satisfied by
 * [LokcalCatalogReader] in production and by a fake in tests, so [RegularMealSource] needn't own the
 * whole reader.
 */
interface FrequentMealProvider {
    suspend fun frequentMeals(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentMeal>
    suspend fun mealItems(mealId: Long): List<LokcalFood>
}

/**
 * The "meals you cook regularly" source: for each meal logged across at least [minWeeks] distinct
 * weeks in the last [windowDays] (auto-inferred from Lokcal's Intake), one suggestion group holding
 * that meal's ingredient foods, with bulk-add wired to "add the whole recipe". Groups come
 * most-regular first — the same ordering [FrequentMealProvider.frequentMeals] returns.
 *
 * Each ingredient is an ordinary catalog-food [Suggestion], so the ViewModel/UI toggle, dedup and
 * "already on the list" marking all apply unchanged — a meal is just a pre-grouped set of foods.
 *
 * Empty on platforms without a Lokcal snapshot (iOS/wasm). A meal with no ingredients is skipped so
 * the UI never renders a bare header.
 */
class RegularMealSource(
    private val meals: FrequentMealProvider,
    private val windowDays: Int = 84,
    private val minWeeks: Int = 2,
    private val limit: Int = 10,
) : RecommendationSource {

    override val id: String = ID

    override suspend fun load(): List<SuggestionGroup> =
        meals.frequentMeals(windowDays, minWeeks, limit).mapNotNull { frequent ->
            val meal = frequent.meal
            val ingredients = meals.mealItems(meal.id)
                .map {
                    Suggestion(
                        key = normalizeKey(it.name),
                        name = it.name,
                        imageUrl = it.imageUrl,
                        lokcalFoodId = it.id,
                    )
                }
                .distinctBy { it.key }
            if (ingredients.isEmpty()) return@mapNotNull null
            SuggestionGroup(
                sourceId = "$ID:${meal.id}",
                title = meal.name,
                suggestions = ingredients,
                supportsBulkAdd = true,
            )
        }

    companion object {
        const val ID = "regular-meals"
    }
}
