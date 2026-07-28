package com.emilflach.groceries.lokcal

import com.emilflach.groceries.Database

sealed interface SyncResult {
    data object Success : SyncResult
    data object NoFolderConfigured : SyncResult
    data object NoBackupFileFound : SyncResult
    data class Failed(val message: String) : SyncResult
}

/**
 * Gets a read-only copy of Lokcal's database onto this device — by syncing from the shared backup
 * folder or importing a `.db` file (Android only). [Database] (used only for sync metadata) is
 * passed per-call because this repository is constructed before the DB finishes loading in `App()`.
 */
expect class LokcalImportRepository {
    suspend fun isFolderConfigured(): Boolean
    suspend fun chooseFolder(): Boolean
    suspend fun syncNow(database: Database): SyncResult
    suspend fun importFromFile(database: Database): SyncResult
    suspend fun lastSyncedAt(database: Database): String?
}
