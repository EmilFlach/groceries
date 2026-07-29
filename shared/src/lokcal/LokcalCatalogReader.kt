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

    /**
     * Foods eaten across at least [minWeeks] distinct calendar weeks within the last [windowDays]
     * — the "regularly bought" signal for weekly-regular recommendations. Most-weeks-first, capped
     * at [limit]. Empty on platforms without a Lokcal snapshot (iOS/wasm).
     */
    suspend fun frequentFoods(windowDays: Int = 84, minWeeks: Int = 3, limit: Int = 40): List<LokcalFrequentFood>
}
