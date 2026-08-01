package com.emilflach.groceries.mealie

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises the Mealie JSON → domain mapping ([toSummary], [toLine], [formatAmount]) against
 * representative explore-endpoint payloads, decoded with the same lenient JSON config the client
 * uses. The HTTP layer itself isn't covered here (it needs a live server); this pins the parsing and
 * field-selection that turn a recipe into shopping-list lines.
 */
class MealieClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun recipeListMapsToSummariesWithImageUrls() {
        // Extra unknown fields (userId, tags, …) must be ignored, mirroring the real payload.
        val payload = """
            {"items":[
              {"id":"r1","slug":"roti","name":"Roti","image":null,"userId":"u","tags":[]},
              {"id":"r2","slug":"pasta","name":"Pasta","image":"h123","totalTime":"20"}
            ]}
        """.trimIndent()

        val summaries = json.decodeFromString<MealieRecipesResponse>(payload).items.mapNotNull { it.toSummary() }

        assertEquals(listOf("Roti", "Pasta"), summaries.map { it.name })
        assertNull(summaries[0].imageUrl, "no image hash → no url")
        assertEquals(
            "$MEALIE_BASE_URL/media/recipes/r2/images/min-original.webp?version=h123",
            summaries[1].imageUrl,
        )
    }

    @Test
    fun recipeDetailMapsIngredientsPreferringFoodNameWithAmount() {
        val payload = """
            {"id":"x","slug":"pasta","name":"Pasta","recipeIngredient":[
              {"quantity":2.0,"unit":{"name":"cup","abbreviation":"cup"},"food":{"name":"Flour"},"note":"sifted","display":"2 cups flour"},
              {"quantity":0.0,"unit":null,"food":null,"note":"Salt to taste","display":"Salt to taste"},
              {"title":"For the sauce","note":"","display":""},
              {"quantity":150.0,"unit":{"name":"gram","abbreviation":"g"},"food":{"name":"Butter"},"note":"","display":"150 g butter"}
            ]}
        """.trimIndent()

        val lines = json.decodeFromString<MealieRecipeDto>(payload).recipeIngredient.mapNotNull { it.toLine() }

        assertEquals(
            listOf(
                MealieIngredientLine("Flour", "2 cup"),
                MealieIngredientLine("Salt to taste", null), // free-text: amount already in the name
                MealieIngredientLine("Butter", "150 g"),      // the section-title row was skipped
            ),
            lines,
        )
    }

    @Test
    fun formatsAmountsForEveryCombination() {
        assertEquals("2 cup", formatAmount(2.0, MealieUnitDto(name = "cup", abbreviation = "cup")))
        assertEquals("0.5 g", formatAmount(0.5, MealieUnitDto(abbreviation = "g")))
        assertEquals("3", formatAmount(3.0, null))                       // quantity only
        assertEquals("g", formatAmount(null, MealieUnitDto(abbreviation = "g"))) // unit only
        assertEquals("g", formatAmount(0.0, MealieUnitDto(abbreviation = "g")))  // zero quantity dropped
        assertNull(formatAmount(null, null))
    }
}
