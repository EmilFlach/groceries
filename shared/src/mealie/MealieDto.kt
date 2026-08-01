package com.emilflach.groceries.mealie

import kotlinx.serialization.Serializable

/**
 * JSON DTOs for the subset of Mealie's REST API that Groceries reads. Parsed leniently with
 * `ignoreUnknownKeys = true` (see [MealieClient]), so only the fields actually used are declared —
 * Mealie returns many more per recipe.
 *
 * Everything is fetched from Mealie's public **explore** endpoints, which need no auth token:
 *  - list:   GET {base}/explore/groups/{group}/recipes
 *  - detail: GET {base}/explore/groups/{group}/recipes/{slug}   (carries `recipeIngredient`)
 */

/** Base URL of the Mealie instance behind recipes.emilflach.com. */
internal const val MEALIE_BASE_URL = "https://mealie.emilflach.com/api"

/** The public group whose recipes are browsable via the explore endpoints. */
internal const val MEALIE_GROUP_SLUG = "home"

@Serializable
internal data class MealieRecipesResponse(
    val items: List<MealieRecipeDto> = emptyList(),
)

@Serializable
internal data class MealieRecipeDto(
    val id: String,
    val slug: String,
    val name: String? = null,
    val image: String? = null,
    val description: String = "",
    // Present only on the recipe-detail endpoint; empty on list items.
    val recipeIngredient: List<MealieIngredientDto> = emptyList(),
)

@Serializable
internal data class MealieIngredientDto(
    val quantity: Double? = null,
    val unit: MealieUnitDto? = null,
    val food: MealieFoodDto? = null,
    val note: String = "",
    val display: String = "",
    // A section heading row ("For the sauce") rather than an actual ingredient — skipped when mapping.
    val title: String? = null,
)

@Serializable
internal data class MealieUnitDto(
    val name: String? = null,
    val abbreviation: String? = null,
)

@Serializable
internal data class MealieFoodDto(
    val name: String? = null,
)
