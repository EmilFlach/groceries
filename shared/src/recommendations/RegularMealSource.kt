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
 * The meals source: one suggestion group per meal (auto-inferred from Lokcal's Intake), holding that
 * meal's ingredient foods with bulk-add wired to "add the whole recipe". Groups come most-regular
 * first — the ordering [FrequentMealProvider.frequentMeals] returns — so the top few read as "your
 * regulars" while the rest of your cooked meals stay available behind the UI's "show more meals".
 *
 * Defaults are deliberately broad ([windowDays] ≈ a year, [minWeeks] = 1) so this surfaces the user's
 * whole recent meal history, not just a tight "cooked ≥2 weeks lately" slice — the UI, not the query,
 * decides how many to show up front.
 *
 * Each ingredient is an ordinary catalog-food [Suggestion], so the ViewModel/UI toggle, dedup and
 * "already on the list" marking all apply unchanged — a meal is just a pre-grouped set of foods.
 *
 * Empty on platforms without a Lokcal snapshot (iOS/wasm). A meal with no ingredients is skipped so
 * the UI never renders a bare header.
 */
class RegularMealSource(
    private val meals: FrequentMealProvider,
    private val windowDays: Int = 365,
    private val minWeeks: Int = 1,
    private val limit: Int = 50,
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
                imageUrl = meal.imageUrl,
            )
        }

    companion object {
        const val ID = "regular-meals"
    }
}
