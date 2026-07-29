package com.emilflach.groceries.viewmodel

import com.emilflach.groceries.data.RegularItemRepository
import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.recommendations.RecommendationRepository
import com.emilflach.groceries.recommendations.Suggestion
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
    private val shoppingList: ShoppingListViewModel,
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _rawGroups = MutableStateFlow<List<com.emilflach.groceries.recommendations.SuggestionGroup>>(emptyList())

    private val _regularKeys = MutableStateFlow<Set<String>>(emptySet())

    /** Normalized keys of the manually-marked regulars, so any card (even one from the search grid)
     *  can show the right "Mark as regular" / "Remove from regulars" action. */
    val regularKeys: StateFlow<Set<String>> = _regularKeys.asStateFlow()

    /**
     * Suggestion groups with added-state derived live from the shopping list: a suggestion is
     * [SuggestionUi.added] when its key matches an active (unchecked) list item, so cards flip the
     * instant the list changes. Added suggestions stay in place (just marked), never removed.
     */
    val groups: StateFlow<List<SuggestionGroupUi>> =
        combine(_rawGroups, shoppingList.items) { groups, items ->
            val activeKeys = items.asSequence()
                .filter { it.checked_at == null }
                .map { normalizeKey(it.name) }
                .toSet()
            groups.map { group ->
                val ui = group.suggestions.map { SuggestionUi(it, it.key in activeKeys) }
                SuggestionGroupUi(
                    sourceId = group.sourceId,
                    title = group.title,
                    items = ui,
                    supportsBulkAdd = group.supportsBulkAdd,
                    allAdded = ui.isNotEmpty() && ui.all { it.added },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        viewModelScope.launch {
            _regularKeys.value = regulars.all().map { it.food_key }.toSet()
            _rawGroups.value = recommendations.load()
        }
    }

    /**
     * Adds the suggestion to the list, or removes it if already there — the single tap toggle.
     * Checks for an active row by key first so a non-deduped manual item can't be added twice and
     * removal can find the real row id.
     */
    fun toggle(suggestion: Suggestion) {
        viewModelScope.launch {
            val active = shoppingList.items.value.firstOrNull {
                it.checked_at == null && normalizeKey(it.name) == suggestion.key
            }
            when {
                active != null -> shoppingList.remove(active.id)
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
