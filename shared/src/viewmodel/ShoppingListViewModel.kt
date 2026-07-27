package com.emilflach.groceries.viewmodel

import com.emilflach.groceries.ShoppingListItem
import com.emilflach.groceries.data.AddItemResult
import com.emilflach.groceries.data.ShoppingListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Simple multiplatform ViewModel-like class (no Android-specific lifecycle dependency),
 * matching the pattern used throughout Lokcal's own viewmodel/ package.
 */
class ShoppingListViewModel(private val repository: ShoppingListRepository) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _items = MutableStateFlow<List<ShoppingListItem>>(emptyList())
    val items: StateFlow<List<ShoppingListItem>> = _items.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _items.value = repository.getAll()
        }
    }

    fun addItem(lokcalFoodId: Long, name: String, imageUrl: String?, onResult: (AddItemResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.add(lokcalFoodId, name, imageUrl)
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

    fun remove(id: Long) {
        viewModelScope.launch {
            repository.remove(id)
            refresh()
        }
    }
}
