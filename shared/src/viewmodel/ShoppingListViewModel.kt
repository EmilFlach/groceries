package com.emilflach.groceries.viewmodel

import com.emilflach.groceries.Aisle
import com.emilflach.groceries.ShoppingListItem
import com.emilflach.groceries.data.AddItemResult
import com.emilflach.groceries.data.FoodLabelRepository
import com.emilflach.groceries.data.ShoppingListRepository
import com.emilflach.groceries.data.normalizeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A run of "to buy" items sharing an aisle. Groups are ordered by the aisle's [sortOrder]; the
 *  catch-all group for unlabeled items uses [aisleId] = null and sorts last. */
data class AisleGroup(
    val aisleId: Long?,
    val title: String,
    val sortOrder: Int,
    val items: List<ShoppingListItem>,
)

/**
 * Simple multiplatform ViewModel-like class (no Android-specific lifecycle dependency),
 * matching the pattern used throughout Lokcal's own viewmodel/ package.
 */
class ShoppingListViewModel(
    private val repository: ShoppingListRepository,
    private val labelRepository: FoodLabelRepository,
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _items = MutableStateFlow<List<ShoppingListItem>>(emptyList())
    val items: StateFlow<List<ShoppingListItem>> = _items.asStateFlow()

    private val _labels = MutableStateFlow<Map<String, Long>>(emptyMap())
    val labels: StateFlow<Map<String, Long>> = _labels.asStateFlow()

    private val _aisles = MutableStateFlow<List<Aisle>>(emptyList())
    val aisles: StateFlow<List<Aisle>> = _aisles.asStateFlow()

    /** The unchecked ("to buy") items grouped and ordered by supermarket aisle, unlabeled last. */
    val toBuyGroups: StateFlow<List<AisleGroup>> =
        combine(_items, _labels, _aisles) { items, labels, aisles ->
            groupByAisle(items.filter { it.checked_at == null }, labels, aisles)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _items.value = repository.getAll()
            _labels.value = labelRepository.labels()
            _aisles.value = labelRepository.aisles()
        }
    }

    fun addItem(lokcalFoodId: Long, name: String, imageUrl: String?, onResult: (AddItemResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.add(lokcalFoodId, name, imageUrl)
            refresh()
            onResult(result)
        }
    }

    fun addManualItem(name: String, onResult: (AddItemResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.addManual(name)
            refresh()
            onResult(result)
        }
    }

    fun setChecked(id: Long, checked: Boolean) {
        viewModelScope.launch {
            repository.setChecked(id, checked)
            refresh()
        }
    }

    fun checkAll() {
        viewModelScope.launch {
            repository.checkAll()
            refresh()
        }
    }

    fun uncheckAll() {
        viewModelScope.launch {
            repository.uncheckAll()
            refresh()
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            repository.remove(id)
            refresh()
        }
    }

    /** Deletes every checked ("in the cart") item — clears the cart to start a fresh weekly list. */
    fun clearChecked() {
        viewModelScope.launch {
            repository.clearChecked()
            refresh()
        }
    }

    /** Assigns [item] (and every other item sharing its normalized name) to the given aisle. */
    fun setLabel(item: ShoppingListItem, aisleId: Long) {
        viewModelScope.launch {
            labelRepository.setLabel(item.name, aisleId)
            refresh()
        }
    }

    fun clearLabel(item: ShoppingListItem) {
        viewModelScope.launch {
            labelRepository.clearLabel(item.name)
            refresh()
        }
    }
}

/**
 * Groups items by their assigned aisle. Groups follow aisle sort order; items whose name has no
 * label (or points at an aisle no longer present) fall into a single "Other" group pinned last.
 * Within a group the incoming order is preserved (the repository already sorts newest-added first).
 */
internal fun groupByAisle(
    items: List<ShoppingListItem>,
    labels: Map<String, Long>,
    aisles: List<Aisle>,
): List<AisleGroup> {
    val byId = aisles.associateBy { it.id }
    val labeled = LinkedHashMap<Long, MutableList<ShoppingListItem>>()
    val other = mutableListOf<ShoppingListItem>()

    for (item in items) {
        val aisleId = labels[normalizeKey(item.name)]
        val aisle = aisleId?.let { byId[it] }
        if (aisle == null) other.add(item)
        else labeled.getOrPut(aisle.id) { mutableListOf() }.add(item)
    }

    val groups = labeled.entries
        .map { (id, groupItems) ->
            val aisle = byId.getValue(id)
            AisleGroup(id, aisle.name, aisle.sort_order.toInt(), groupItems)
        }
        .sortedWith(compareBy({ it.sortOrder }, { it.title }))
        .toMutableList()

    if (other.isNotEmpty()) {
        groups += AisleGroup(null, "Other", Int.MAX_VALUE, other)
    }
    return groups
}
