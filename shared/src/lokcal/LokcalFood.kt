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
