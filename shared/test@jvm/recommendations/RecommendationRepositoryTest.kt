package com.emilflach.groceries.recommendations

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecommendationRepositoryTest {

    private fun suggestion(key: String) =
        Suggestion(key = key, name = key, imageUrl = null, lokcalFoodId = null)

    private fun source(id: String, vararg groups: SuggestionGroup) = object : RecommendationSource {
        override val id = id
        override suspend fun load() = groups.toList()
    }

    @Test
    fun dedupsWithinGroupByKey() = runTest {
        val repo = RecommendationRepository(
            listOf(
                source(
                    "a",
                    SuggestionGroup("a", "A", listOf(suggestion("milk"), suggestion("milk"), suggestion("eggs")), true),
                ),
            ),
        )
        assertEquals(listOf("milk", "eggs"), repo.load().single().suggestions.map { it.key })
    }

    @Test
    fun laterSourceLosesSharedKey() = runTest {
        val repo = RecommendationRepository(
            listOf(
                source("a", SuggestionGroup("a", "A", listOf(suggestion("milk")), true)),
                source("b", SuggestionGroup("b", "B", listOf(suggestion("milk"), suggestion("eggs")), true)),
            ),
        )
        val groups = repo.load()
        assertEquals(listOf("milk"), groups[0].suggestions.map { it.key })
        assertEquals(listOf("eggs"), groups[1].suggestions.map { it.key }, "milk already surfaced by source a")
    }

    @Test
    fun dropsEmptyGroups() = runTest {
        val repo = RecommendationRepository(
            listOf(source("a", SuggestionGroup("a", "A", emptyList(), true))),
        )
        assertEquals(emptyList(), repo.load())
    }

    @Test
    fun throwingSourceDoesNotSinkOthers() = runTest {
        val boom = object : RecommendationSource {
            override val id = "boom"
            override suspend fun load(): List<SuggestionGroup> = throw IllegalStateException("nope")
        }
        val repo = RecommendationRepository(
            listOf(boom, source("b", SuggestionGroup("b", "B", listOf(suggestion("eggs")), true))),
        )
        assertEquals(listOf("eggs"), repo.load().single().suggestions.map { it.key })
    }
}
