package com.emilflach.groceries.recommendations

/**
 * One recommended food. [key] is `normalizeKey(name)` — the single identity used both for
 * dedup across sources and for reflecting "already on the list" state in the UI.
 *
 * @param lokcalFoodId the Lokcal catalog id when this came from a catalog food; null for
 *   free-typed regulars (which get a synthetic id when added, like other manual items).
 * @param note text pre-filled onto the shopping-list item when added — the required grams for a
 *   meal ingredient; null when there's nothing to prefill (regulars, plain search results).
 */
data class Suggestion(
    val key: String,
    val name: String,
    val imageUrl: String?,
    val lokcalFoodId: Long?,
    val note: String? = null,
)

/**
 * A titled block of suggestions from one source, e.g. "Regulars" or a recipe's ingredients.
 *
 * @param supportsBulkAdd whether the UI should offer an "Add all" action for this group (true for
 *   regulars and, later, "add the whole recipe").
 * @param imageUrl an optional thumbnail for the group header — set for meals (the meal's photo);
 *   null for the flat food groups.
 */
data class SuggestionGroup(
    val sourceId: String,
    val title: String,
    val suggestions: List<Suggestion>,
    val supportsBulkAdd: Boolean,
    val imageUrl: String? = null,
)

/**
 * A pluggable producer of suggestion groups. New recommendation kinds (regulars today;
 * recipe ingredients, "meals you haven't had lately", etc. later) implement this and are added to
 * the [RecommendationRepository]'s source list without touching existing sources.
 *
 * Implementations MUST return an empty list — never throw — when they have no data or the current
 * platform can't read their backing store (e.g. Lokcal-backed sources on iOS/wasm). The aggregator
 * additionally guards against exceptions so one failing source can't sink the rest.
 */
interface RecommendationSource {
    val id: String
    suspend fun load(): List<SuggestionGroup>
}
