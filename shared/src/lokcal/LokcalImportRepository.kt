package com.emilflach.groceries.lokcal

import com.emilflach.groceries.Database

sealed interface SyncResult {
    data object Success : SyncResult
    data object NoFolderConfigured : SyncResult
    data object NoBackupFileFound : SyncResult
    data class Failed(val message: String) : SyncResult
}

/**
 * Orchestrates getting a read-only copy of Lokcal's database onto this device: either by
 * syncing from the shared folder Lokcal's nightly Android backup writes to, or by manually
 * importing a single exported `.db` file. Only Android implements this today.
 *
 * [Database] (Groceries' own, used only to record sync-status metadata) is passed per-call
 * rather than held at construction time, since this repository must be constructable
 * before the database finishes its async load in `App()`.
 */
expect class LokcalImportRepository {
    suspend fun isFolderConfigured(): Boolean
    suspend fun chooseFolder(): Boolean
    suspend fun syncNow(database: Database): SyncResult
    suspend fun importFromFile(database: Database): SyncResult
    suspend fun lastSyncedAt(database: Database): String?
}
