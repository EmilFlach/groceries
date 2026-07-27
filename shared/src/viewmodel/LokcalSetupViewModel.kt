package com.emilflach.groceries.viewmodel

import com.emilflach.groceries.Database
import com.emilflach.groceries.lokcal.LokcalImportRepository
import com.emilflach.groceries.lokcal.SyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LokcalSetupUiState(
    val folderConfigured: Boolean = false,
    val lastSyncedAt: String? = null,
    val isBusy: Boolean = false,
    val lastResult: SyncResult? = null,
)

class LokcalSetupViewModel(
    private val repository: LokcalImportRepository,
    private val database: Database,
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(LokcalSetupUiState())
    val uiState: StateFlow<LokcalSetupUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                folderConfigured = repository.isFolderConfigured(),
                lastSyncedAt = repository.lastSyncedAt(database),
            )
        }
    }

    fun chooseFolder() {
        viewModelScope.launch {
            if (repository.chooseFolder()) {
                syncNow()
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val result = repository.syncNow(database)
            _uiState.value = _uiState.value.copy(isBusy = false, lastResult = result)
            refreshStatus()
        }
    }

    fun importFromFile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val result = repository.importFromFile(database)
            _uiState.value = _uiState.value.copy(isBusy = false, lastResult = result)
            refreshStatus()
        }
    }
}
