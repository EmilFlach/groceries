package com.emilflach.groceries.lokcal

actual class LokcalCatalogReader {
    actual suspend fun hasSnapshot(): Boolean = false
    actual suspend fun browseFoods(limit: Int): List<LokcalFood> = emptyList()
    actual suspend fun searchFoods(query: String): List<LokcalFood> = emptyList()
    actual suspend fun browseMealImages(limit: Int): List<String> = emptyList()
    actual suspend fun frequentFoods(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentFood> = emptyList()
    actual suspend fun frequentMeals(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentMeal> = emptyList()
    actual suspend fun mealItems(mealId: Long): List<LokcalMealItem> = emptyList()
}
