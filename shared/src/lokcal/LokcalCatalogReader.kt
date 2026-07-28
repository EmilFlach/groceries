package com.emilflach.groceries.lokcal

/**
 * Reads from a locally-imported, read-only snapshot of Lokcal's database.
 * Only the Android actual is backed by a real snapshot today (via SAF folder sync or
 * manual file import, see [LokcalImportRepository]); other platforms stub this out.
 */
expect class LokcalCatalogReader {
    suspend fun hasSnapshot(): Boolean
    suspend fun browseFoods(limit: Int = 100): List<LokcalFood>
    suspend fun searchFoods(query: String): List<LokcalFood>

    /** Image URLs of meals that have a photo (newest first) — the collage prefers these over food photos. */
    suspend fun browseMealImages(limit: Int = 100): List<String>
}
