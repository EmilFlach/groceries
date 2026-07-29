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
 * The "Weekly regulars" source: the foods you buy most often, gathered from two inputs and merged
 * into a single group.
 *
 *  - **Auto-inferred** from Lokcal's Intake log via [FrequentFoodProvider] — foods eaten across at
 *    least [minWeeks] distinct weeks in the last [windowDays]. Empty on platforms without a Lokcal
 *    snapshot (iOS/wasm).
 *  - **Manually marked** regulars from the Groceries DB ([RegularItemRepository]) — works on every
 *    platform, and lets the user pin items Lokcal wouldn't know about (e.g. household goods).
 *
 * Manual pins come first and win on identity collision (they carry the user's chosen name/image),
 * deduped with the auto list by [normalizeKey].
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
        val manual = regulars.all().map {
            Suggestion(
                key = it.food_key,
                name = it.name,
                imageUrl = it.image_url,
                lokcalFoodId = it.lokcal_food_id,
                reason = "Marked as regular",
            )
        }
        val weeksInWindow = windowDays / 7
        val auto = frequentFoods.frequentFoods(windowDays, minWeeks, limit).map {
            Suggestion(
                key = normalizeKey(it.food.name),
                name = it.food.name,
                imageUrl = it.food.imageUrl,
                lokcalFoodId = it.food.id,
                reason = "${it.distinctWeeks} of the last $weeksInWindow weeks",
            )
        }

        val merged = (manual + auto).distinctBy { it.key }
        return if (merged.isEmpty()) emptyList()
        else listOf(SuggestionGroup(id, "Weekly regulars", merged, supportsBulkAdd = true))
    }

    companion object {
        const val ID = "weekly-regulars"
    }
}
