package com.emilflach.groceries.recommendations

import com.emilflach.groceries.data.RegularItemRepository
import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalFrequentFood

/** Looks up the foods eaten across many recent weeks — satisfied by [LokcalCatalogReader.frequentFoods]
 *  in production and by a fake in tests, so [WeeklyRegularSource] needn't own the whole reader. */
fun interface FrequentFoodProvider {
    suspend fun frequentFoods(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentFood>
}

/**
 * The foods you buy most often, gathered from two inputs kept in *separate* groups so the user's
 * hand-curated regulars aren't diluted by machine estimates:
 *
 *  - **"Regulars"** — the [RegularItemRepository] items the user explicitly marked. Works on
 *    every platform, and lets them pin things Lokcal wouldn't know about (e.g. household goods).
 *  - **"Suggested"** — auto-inferred from Lokcal's Intake log via [FrequentFoodProvider]: foods
 *    eaten across at least [minWeeks] distinct weeks in the last [windowDays]. Empty on platforms
 *    without a Lokcal snapshot (iOS/wasm).
 *
 * An auto food that's already a manual regular is dropped from "Suggested" (deduped by
 * [normalizeKey]), so a food never shows in both — the manual pin wins.
 */
class WeeklyRegularSource(
    private val frequentFoods: FrequentFoodProvider,
    private val regulars: RegularItemRepository,
    private val windowDays: Int = 84,
    private val minWeeks: Int = 3,
    private val limit: Int = 40,
) : RecommendationSource {

    override val id: String = ID

    override suspend fun load(): List<SuggestionGroup> {
        val manual = regulars.all()
            .map { Suggestion(key = it.food_key, name = it.name, imageUrl = it.image_url, lokcalFoodId = it.lokcal_food_id) }
            .distinctBy { it.key }
        val manualKeys = manual.mapTo(HashSet()) { it.key }
        val suggested = frequentFoods.frequentFoods(windowDays, minWeeks, limit)
            .map { Suggestion(key = normalizeKey(it.food.name), name = it.food.name, imageUrl = it.food.imageUrl, lokcalFoodId = it.food.id) }
            .distinctBy { it.key }
            .filterNot { it.key in manualKeys }

        return buildList {
            if (manual.isNotEmpty()) add(SuggestionGroup(MANUAL_ID, "Regulars", manual, supportsBulkAdd = true))
            if (suggested.isNotEmpty()) add(SuggestionGroup(SUGGESTED_ID, "Suggested", suggested, supportsBulkAdd = true))
        }
    }

    companion object {
        const val ID = "weekly-regulars"
        const val MANUAL_ID = "weekly-regulars"
        const val SUGGESTED_ID = "suggested"
    }
}
