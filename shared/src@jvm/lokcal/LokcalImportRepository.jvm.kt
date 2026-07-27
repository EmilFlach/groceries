package com.emilflach.groceries.lokcal

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.emilflach.groceries.Database
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.write
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val NOT_SUPPORTED_MESSAGE = "Syncing from Lokcal is only available on Android right now"
private const val LAST_SYNCED_META_KEY = "lokcal_last_synced_at"

actual class LokcalImportRepository {
    actual suspend fun isFolderConfigured(): Boolean = false
    actual suspend fun chooseFolder(): Boolean = false
    actual suspend fun syncNow(database: Database): SyncResult = SyncResult.Failed(NOT_SUPPORTED_MESSAGE)

    actual suspend fun importFromFile(database: Database): SyncResult {
        val picked = FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("db")))
            ?: return SyncResult.Failed("No file selected")

        return try {
            copyIntoSnapshot(picked)
            recordSyncedNow(database)
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "Import failed")
        }
    }

    actual suspend fun lastSyncedAt(database: Database): String? =
        database.metaQueries.getMeta(LAST_SYNCED_META_KEY).awaitAsOneOrNull()

    private suspend fun copyIntoSnapshot(source: PlatformFile) {
        val finalFile = lokcalSnapshotFile()
        finalFile.parentFile?.mkdirs()
        val tempFile = File(finalFile.path + ".tmp")

        PlatformFile(tempFile.path).write(source)

        if (finalFile.exists()) finalFile.delete()
        check(tempFile.renameTo(finalFile)) { "Could not finalize the imported snapshot" }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun recordSyncedNow(database: Database) {
        database.metaQueries.setMeta(LAST_SYNCED_META_KEY, Clock.System.now().toString())
    }
}
