package com.emilflach.groceries.lokcal

import com.emilflach.groceries.Database

private const val NOT_SUPPORTED_MESSAGE = "Syncing from Lokcal is only available on Android right now"

actual class LokcalImportRepository {
    actual suspend fun isFolderConfigured(): Boolean = false
    actual suspend fun chooseFolder(): Boolean = false
    actual suspend fun syncNow(database: Database): SyncResult = SyncResult.Failed(NOT_SUPPORTED_MESSAGE)
    actual suspend fun importFromFile(database: Database): SyncResult = SyncResult.Failed(NOT_SUPPORTED_MESSAGE)
    actual suspend fun lastSyncedAt(database: Database): String? = null
}
