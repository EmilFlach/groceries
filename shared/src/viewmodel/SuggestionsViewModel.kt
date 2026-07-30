package com.emilflach.groceries.viewmodel

import com.emilflach.groceries.data.DismissedSuggestionRepository
import com.emilflach.groceries.data.RegularItemRepository
import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.recommendations.RecommendationRepository
import com.emilflach.groceries.recommendations.RegularMealSource
import com.emilflach.groceries.recommendations.Suggestion
import com.emilflach.groceries.recommendations.WeeklyRegularSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A [Suggestion] plus whether it's currently on the active shopping list (drives the added mark). */
data class SuggestionUi(val suggestion: Suggestion, val added: Boolean)

/** A source's group, resolved for display. [allAdded] hides "Add all" once everything's on the list. */
data class SuggestionGroupUi(
    val sourceId: String,
    val title: String,
    val items: List<SuggestionUi>,
    val supportsBulkAdd: Boolean,
    val allAdded: Boolean,
)

/**
 * Drives the recommendations part of the "Add" hub. Follows the plain-class StateFlow pattern used
 * across the app (no androidx ViewModel). Reuses the shared [ShoppingListViewModel] instance for
 * both its live `items` flow (to derive added-state) and its add/remove actions, so a tapped
 * suggestion and the list stay in sync with no extra plumbing.
 */
class SuggestionsViewModel(
    private val recommendations: RecommendationRepository,
    private val regulars: RegularItemRepository,
    private val dismissed: DismissedSuggestionRepository,
    private val shoppingList: ShoppingListViewModel,
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _rawGroups = MutableStateFlow<List<com.emilflach.groceries.recommendations.SuggestionGroup>>(emptyList())

    private val _regularKeys = MutableStateFlow<Set<String>>(emptySet())

    // Dismissed ("Not interested") keys, kept as live state so a dismissal hides its card/group at
    // once — the combine below re-runs without waiting for the next recommendations reload.
    private val _dismissedFoodKeys = MutableStateFlow<Set<String>>(emptySet())
    private val _dismissedMealKeys = MutableStateFlow<Set<String>>(emptySet())

    /** Normalized keys of the manually-marked regulars, so any card (even one from the search grid)
     *  can show the right "Mark as regular" / "Remove from regulars" action. */
    val regularKeys: StateFlow<Set<String>> = _regularKeys.asStateFlow()

    /** Normalized key → assigned aisle name, derived from the shared list's labels + aisles, so a
     *  non-regular suggestion card can show which aisle it belongs to. Absent key = no aisle. */
    val aisleNames: StateFlow<Map<String, String>> =
        combine(shoppingList.labels, shoppingList.aisles) { labels, aisles ->
            val nameById = aisles.associate { it.id to it.name }
            labels.mapNotNull { (key, aisleId) -> nameById[aisleId]?.let { key to it } }.toMap()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Suggestion groups with added-state derived live from the shopping list: a suggestion is
     * [SuggestionUi.added] when its key matches *any* list item — checked (in the cart) or unchecked
     * — so an item already on the list never reads as addable. Counting only unchecked items here
     * would show an in-cart item as not-added and let [toggle] add a duplicate active row, which the
     * partial unique index then rejects when it's unchecked. Added suggestions stay in place (just
     * marked), never removed.
     */
    val groups: StateFlow<List<SuggestionGroupUi>> =
        combine(
            _rawGroups,
            shoppingList.items,
            _regularKeys,
            _dismissedFoodKeys,
            _dismissedMealKeys,
        ) { groups, items, regularKeys, dismissedFoods, dismissedMeals ->
            val onListKeys = items.asSequence()
                .map { normalizeKey(it.name) }
                .toSet()
            groups.mapNotNull { group ->
                val isMeal = group.sourceId.startsWith(RegularMealSource.ID)
                // A dismissed meal hides its whole group; keyed by the meal name so it survives a
                // re-import (meal ids can change) and matches DismissedSuggestionRepository.
                if (isMeal && normalizeKey(group.title) in dismissedMeals) return@mapNotNull null
                val suggestions = when {
                    // The user's own pins, shown verbatim.
                    group.sourceId == WeeklyRegularSource.MANUAL_ID -> group.suggestions
                    // Meals are complete recipes: every ingredient shows (even ones that are regulars
                    // or appear elsewhere) — only an explicit "Not interested" hides one.
                    isMeal -> group.suggestions.filterNot { it.key in dismissedFoods }
                    // "Suggested": drop anything already a manual regular, plus dismissed foods.
                    // Filtered live so marking/dismissing takes effect at once, not on the next reload.
                    else -> group.suggestions.filterNot { it.key in regularKeys || it.key in dismissedFoods }
                }
                if (suggestions.isEmpty()) return@mapNotNull null
                val ui = suggestions.map { SuggestionUi(it, it.key in onListKeys) }
                SuggestionGroupUi(
                    sourceId = group.sourceId,
                    title = group.title,
                    items = ui,
                    supportsBulkAdd = group.supportsBulkAdd,
                    allAdded = ui.all { it.added },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        viewModelScope.launch {
            _regularKeys.value = regulars.all().map { it.food_key }.toSet()
            val d = dismissed.all()
            _dismissedFoodKeys.value = d.foods
            _dismissedMealKeys.value = d.meals
            _rawGroups.value = recommendations.load()
        }
    }

    /** Dismisses an individual food suggestion ("Not interested"): hides it from "Suggested" and any
     *  meal's ingredients. Persisted, and applied to the live [groups] at once. Undo via [restore]. */
    fun dismiss(suggestion: Suggestion) {
        _dismissedFoodKeys.value = _dismissedFoodKeys.value + suggestion.key
        viewModelScope.launch { dismissed.dismissFood(suggestion.name) }
    }

    fun restore(suggestion: Suggestion) {
        _dismissedFoodKeys.value = _dismissedFoodKeys.value - suggestion.key
        viewModelScope.launch { dismissed.restoreFood(suggestion.name) }
    }

    /** Dismisses a whole meal group by its title; hides the group. Persisted + live. Undo via
     *  [restoreMeal]. */
    fun dismissMeal(group: SuggestionGroupUi) {
        _dismissedMealKeys.value = _dismissedMealKeys.value + normalizeKey(group.title)
        viewModelScope.launch { dismissed.dismissMeal(group.title) }
    }

    fun restoreMeal(group: SuggestionGroupUi) {
        _dismissedMealKeys.value = _dismissedMealKeys.value - normalizeKey(group.title)
        viewModelScope.launch { dismissed.restoreMeal(group.title) }
    }

    /**
     * Adds the suggestion to the list, or removes it if already there — the single tap toggle.
     * Matches every row for the key (checked *or* unchecked) so an in-cart item toggles off instead
     * of being re-added as a duplicate — which would collide on the active-food unique index once
     * unchecked. Removes all matching rows so a food that's both checked and active clears fully.
     */
    fun toggle(suggestion: Suggestion) {
        viewModelScope.launch {
            val existing = shoppingList.items.value.filter { normalizeKey(it.name) == suggestion.key }
            when {
                existing.isNotEmpty() -> existing.forEach { shoppingList.remove(it.id) }
                suggestion.lokcalFoodId != null ->
                    shoppingList.addItem(suggestion.lokcalFoodId, suggestion.name, suggestion.imageUrl)
                else -> shoppingList.addManualItem(suggestion.name)
            }
        }
    }

    /** Adds every not-yet-added item in the group (the group's "Add all"). */
    fun addAll(group: SuggestionGroupUi) {
        group.items.filterNot { it.added }.forEach { toggle(it.suggestion) }
    }

    /** Toggles whether the suggestion is a manually-marked regular, then reloads so it re-groups. */
    fun markRegular(suggestion: Suggestion) {
        viewModelScope.launch {
            if (regulars.isRegular(suggestion.name)) regulars.unmark(suggestion.name)
            else regulars.mark(suggestion.name, suggestion.imageUrl, suggestion.lokcalFoodId)
            refresh()
        }
    }
}
