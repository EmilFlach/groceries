package com.emilflach.groceries.lokcal

actual class LokcalCatalogReader {
    actual suspend fun hasSnapshot(): Boolean = false
    actual suspend fun browseFoods(limit: Int): List<LokcalFood> = emptyList()
    actual suspend fun searchFoods(query: String): List<LokcalFood> = emptyList()
}
