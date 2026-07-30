package com.emilflach.groceries.recommendations

/**
 * Aggregates every [RecommendationSource] into the flat list of groups the UI renders, in source
 * declaration order.
 *
 * Duplicates are removed only *within* a group (by [Suggestion.key]), never across groups: the same
 * food may legitimately appear in more than one place — an ingredient shared by several meals, or a
 * food that's both a suggestion and a meal ingredient — and each group must stand on its own so a
 * meal shows its complete recipe. The cross-group rules that DO apply ("Suggested" excludes manual
 * regulars; dismissed items are hidden) live in [WeeklyRegularSource] and the ViewModel, next to the
 * state they depend on. A source that throws is skipped rather than sinking the whole load, and empty
 * groups are dropped so the UI never renders a bare header.
 */
class RecommendationRepository(private val sources: List<RecommendationSource>) {

    suspend fun load(): List<SuggestionGroup> =
        sources
            .flatMap { source -> runCatching { source.load() }.getOrDefault(emptyList()) }
            .map { group -> group.copy(suggestions = group.suggestions.distinctBy { it.key }) }
            .filter { it.suggestions.isNotEmpty() }
}
