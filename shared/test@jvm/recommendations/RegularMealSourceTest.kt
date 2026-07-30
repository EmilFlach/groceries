package com.emilflach.groceries.recommendations

import com.emilflach.groceries.lokcal.LokcalFood
import com.emilflach.groceries.lokcal.LokcalFrequentMeal
import com.emilflach.groceries.lokcal.LokcalMeal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegularMealSourceTest {

    private fun food(id: Long, name: String) = LokcalFood(id, name, 0.0, null, null, null, null)

    private fun frequent(id: Long, name: String, weeks: Int = 3) =
        LokcalFrequentMeal(LokcalMeal(id, name, null), distinctWeeks = weeks, lastEaten = null)

    /** A fake provider: fixed meals (in the order given) and a per-meal-id ingredient map. */
    private fun provider(
        meals: List<LokcalFrequentMeal>,
        items: Map<Long, List<LokcalFood>>,
    ) = object : FrequentMealProvider {
        override suspend fun frequentMeals(windowDays: Int, minWeeks: Int, limit: Int) = meals
        override suspend fun mealItems(mealId: Long) = items[mealId].orEmpty()
    }

    @Test
    fun oneGroupPerMealWithItsIngredients() = runTest {
        val source = RegularMealSource(
            provider(
                meals = listOf(frequent(1, "Pasta"), frequent(2, "Curry")),
                items = mapOf(
                    1L to listOf(food(10, "Spaghetti"), food(11, "Tomato")),
                    2L to listOf(food(20, "Rice"), food(21, "Chicken")),
                ),
            ),
        )

        val groups = source.load()
        // Order preserved (already most-regular first from the provider); one group per meal.
        assertEquals(listOf("Pasta", "Curry"), groups.map { it.title })
        assertTrue(groups.all { it.supportsBulkAdd }, "each meal offers 'add the whole recipe'")
        assertEquals(listOf("spaghetti", "tomato"), groups[0].suggestions.map { it.key })
        assertEquals(10L, groups[0].suggestions[0].lokcalFoodId)
        assertEquals(listOf("regular-meals:1", "regular-meals:2"), groups.map { it.sourceId })
    }

    @Test
    fun skipsMealsWithNoIngredients() = runTest {
        val source = RegularMealSource(
            provider(
                meals = listOf(frequent(1, "Empty"), frequent(2, "Curry")),
                items = mapOf(2L to listOf(food(20, "Rice"))),
            ),
        )

        assertEquals(listOf("Curry"), source.load().map { it.title })
    }

    @Test
    fun dedupsRepeatedIngredientWithinAMeal() = runTest {
        val source = RegularMealSource(
            provider(
                meals = listOf(frequent(1, "Pasta")),
                items = mapOf(1L to listOf(food(10, "Tomato"), food(11, "tomato"))),
            ),
        )

        assertEquals(listOf("tomato"), source.load().single().suggestions.map { it.key })
    }

    @Test
    fun emptyWhenNoMeals() = runTest {
        assertEquals(emptyList(), RegularMealSource(provider(emptyList(), emptyMap())).load())
    }
}
