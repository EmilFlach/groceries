package com.emilflach.groceries.mealie

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** A Mealie recipe as shown in search results — the light summary from the list endpoint. */
data class MealieRecipeSummary(
    val id: String,
    val slug: String,
    val name: String,
    val imageUrl: String?,
)

/** One ingredient line of a recipe, ready to drop onto the shopping list as a custom item. */
data class MealieIngredientLine(
    val name: String,
    /** A grams/amount hint (e.g. "2 cups", "150 g"), or null when the amount is already in [name]. */
    val note: String?,
)

/**
 * Read-only client over Mealie's public explore API (see [MealieDto]). Mirrors the caching shape of
 * the recipes.emilflach.com app: the full recipe list is fetched once and kept in memory (the
 * collection is small and this feature is used rarely), and search filters that list by name.
 * Recipe ingredients are fetched per slug on demand and cached.
 *
 * Every call is wrapped so it **never throws** — a network failure, a missing engine (web), or a
 * parse error all surface as empty results, so search degrades gracefully instead of crashing the
 * Add hub.
 */
class MealieClient(engineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>? = mealieHttpEngineOrNull()) {

    private val client: HttpClient? = engineFactory?.let { factory ->
        HttpClient(factory) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    private val mutex = Mutex()
    private var cachedRecipes: List<MealieRecipeSummary>? = null
    private val ingredientCache = mutableMapOf<String, List<MealieIngredientLine>>()

    /** All recipes, fetched once and cached. Empty on failure or when no engine is available. */
    private suspend fun allRecipes(): List<MealieRecipeSummary> {
        val http = client ?: return emptyList()
        return mutex.withLock {
            cachedRecipes ?: run {
                val loaded = runCatching {
                    val response: MealieRecipesResponse =
                        http.get("$MEALIE_BASE_URL/explore/groups/$MEALIE_GROUP_SLUG/recipes") {
                            parameter("perPage", "-1") // Mealie: -1 = no pagination, return everything.
                            parameter("orderBy", "name")
                        }.body()
                    response.items.mapNotNull { it.toSummary() }
                }.getOrDefault(emptyList())
                // Cache only a successful, non-empty load so a transient failure can be retried later.
                if (loaded.isNotEmpty()) cachedRecipes = loaded
                loaded
            }
        }
    }

    /** Recipes whose name contains [query] (case-insensitive). Empty for a blank query. */
    suspend fun searchRecipes(query: String): List<MealieRecipeSummary> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return allRecipes().filter { it.name.lowercase().contains(needle) }
    }

    /** The ingredient lines of one recipe, fetched on demand and cached. Empty on failure. */
    suspend fun recipeIngredients(slug: String): List<MealieIngredientLine> {
        val http = client ?: return emptyList()
        return mutex.withLock {
            ingredientCache[slug] ?: run {
                val loaded = runCatching {
                    val recipe: MealieRecipeDto =
                        http.get("$MEALIE_BASE_URL/explore/groups/$MEALIE_GROUP_SLUG/recipes/$slug").body()
                    recipe.recipeIngredient.mapNotNull { it.toLine() }
                }.getOrDefault(emptyList())
                if (loaded.isNotEmpty()) ingredientCache[slug] = loaded
                loaded
            }
        }
    }
}

/** Builds the Mealie media URL for a recipe photo; null when the recipe has no image. */
internal fun MealieRecipeDto.toSummary(): MealieRecipeSummary? {
    val displayName = name?.trim().orEmpty()
    if (displayName.isEmpty()) return null
    val imageUrl = image?.takeIf { it.isNotBlank() }?.let {
        "$MEALIE_BASE_URL/media/recipes/$id/images/min-original.webp?version=$it"
    }
    return MealieRecipeSummary(id = id, slug = slug, name = displayName, imageUrl = imageUrl)
}

/**
 * Maps a Mealie ingredient to a shopping-list line. Prefers the structured food name (with the
 * amount as a note); otherwise falls back to the free-text note/display (which already embed the
 * amount, so no separate note is added). Section-title rows and empty ingredients map to null.
 */
internal fun MealieIngredientDto.toLine(): MealieIngredientLine? {
    val foodName = food?.name?.trim().orEmpty()
    val noteText = note.trim()
    val displayText = display.trim()
    return when {
        foodName.isNotEmpty() -> MealieIngredientLine(foodName, formatAmount(quantity, unit))
        noteText.isNotEmpty() -> MealieIngredientLine(noteText, null)
        displayText.isNotEmpty() -> MealieIngredientLine(displayText, null)
        else -> null
    }
}

/** Formats a quantity + unit as a short amount string (e.g. "2 cups", "150 g"), or null. */
internal fun formatAmount(quantity: Double?, unit: MealieUnitDto?): String? {
    val qty = quantity?.takeIf { it > 0.0 }?.let { formatQuantity(it) }
    val unitLabel = (unit?.abbreviation?.trim()?.takeIf { it.isNotEmpty() }
        ?: unit?.name?.trim()?.takeIf { it.isNotEmpty() })
    return when {
        qty != null && unitLabel != null -> "$qty $unitLabel"
        qty != null -> qty
        unitLabel != null -> unitLabel
        else -> null
    }
}

/** Drops a trailing ".0" so whole amounts read "2" rather than "2.0". */
private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
