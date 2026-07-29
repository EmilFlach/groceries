package com.emilflach.groceries.recommendations

/**
 * One recommended food. [key] is `normalizeKey(name)` — the single identity used both for
 * dedup across sources and for reflecting "already on the list" state in the UI.
 *
 * @param lokcalFoodId the Lokcal catalog id when this came from a catalog food; null for
 *   free-typed regulars (which get a synthetic id when added, like other manual items).
 * @param reason a short sub-label shown under the name, e.g. "5 of the last 12 weeks".
 */
data class Suggestion(
    val key: String,
    val name: String,
    val imageUrl: String?,
    val lokcalFoodId: Long?,
    val reason: String? = null,
)

/**
 * A titled block of suggestions from one source, e.g. "Weekly regulars" or a recipe's ingredients.
 *
 * @param supportsBulkAdd whether the UI should offer an "Add all" action for this group (true for
 *   weekly regulars and, later, "add the whole recipe").
 */
data class SuggestionGroup(
    val sourceId: String,
    val title: String,
    val suggestions: List<Suggestion>,
    val supportsBulkAdd: Boolean,
)

/**
 * A pluggable producer of suggestion groups. New recommendation kinds (weekly regulars today;
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
