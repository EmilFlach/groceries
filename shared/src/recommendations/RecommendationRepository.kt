package com.emilflach.groceries.recommendations

/**
 * Aggregates every [RecommendationSource] into the flat list of groups the UI renders.
 *
 * Sources run in declaration order, which is also their priority: if two sources surface the same
 * food (by [Suggestion.key]), the earlier source keeps it and later ones drop it, so a food never
 * appears in two groups. A source that throws is skipped (its groups are simply absent) rather than
 * failing the whole load, and empty groups are dropped so the UI never renders a bare header.
 *
 * This is deliberately pure — it only combines source output. Dedup against the *current shopping
 * list* and "already added" marking are view concerns handled in the ViewModel, keeping this
 * trivially unit-testable.
 */
class RecommendationRepository(private val sources: List<RecommendationSource>) {

    suspend fun load(): List<SuggestionGroup> {
        val seen = HashSet<String>()
        return sources
            .flatMap { source -> runCatching { source.load() }.getOrDefault(emptyList()) }
            .map { group ->
                val kept = group.suggestions
                    .distinctBy { it.key }
                    .filter { seen.add(it.key) }
                group.copy(suggestions = kept)
            }
            .filter { it.suggestions.isNotEmpty() }
    }
}
