package com.emilflach.groceries.viewmodel

import com.emilflach.groceries.Aisle
import com.emilflach.groceries.data.FoodLabelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs the aisle-management settings screen: reorder, rename, add and delete supermarket aisles. */
class AisleSettingsViewModel(private val repository: FoodLabelRepository) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _aisles = MutableStateFlow<List<Aisle>>(emptyList())
    val aisles: StateFlow<List<Aisle>> = _aisles.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _aisles.value = repository.aisles() }
    }

    fun add(name: String) {
        viewModelScope.launch {
            repository.addAisle(name)
            refresh()
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            repository.renameAisle(id, name)
            refresh()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.deleteAisle(id)
            refresh()
        }
    }

    fun moveUp(id: Long) = move(id, -1)
    fun moveDown(id: Long) = move(id, +1)

    /** Swaps the aisle with its neighbour in the given direction and persists the new walk order. */
    private fun move(id: Long, direction: Int) {
        val current = _aisles.value
        val index = current.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target !in current.indices) return

        val reordered = current.toMutableList().apply {
            val moved = removeAt(index)
            add(target, moved)
        }
        // Optimistic update so the arrows feel instant; the DB write reconciles on refresh.
        _aisles.value = reordered
        viewModelScope.launch {
            repository.reorderAisles(reordered.map { it.id })
            refresh()
        }
    }
}
