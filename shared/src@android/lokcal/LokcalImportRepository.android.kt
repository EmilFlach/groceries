package com.emilflach.groceries.lokcal

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.emilflach.groceries.Database
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import io.github.vinceglb.filekit.write
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val LAST_SYNCED_META_KEY = "lokcal_last_synced_at"
private val BACKUP_NAME_REGEX = Regex("""^lokcal-backup-\d+\.db$""")

actual class LokcalImportRepository(private val context: Context) {
    actual suspend fun isFolderConfigured(): Boolean = LokcalBookmarkStore.load() != null

    actual suspend fun chooseFolder(): Boolean {
        val directory = FileKit.openDirectoryPicker() ?: return false
        LokcalBookmarkStore.save(directory)
        return true
    }

    actual suspend fun syncNow(database: Database): SyncResult {
        val directory = LokcalBookmarkStore.load() ?: return SyncResult.NoFolderConfigured

        return try {
            directory.startAccessingSecurityScopedResource()
            try {
                val treeUri = (directory.androidFile as AndroidFile.UriWrapper).uri
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                    ?: return SyncResult.Failed("Could not open the configured folder")

                val allFiles = docDir.listFiles().toList()
                val backupNames = allFiles.mapNotNull { it.name }.filter { BACKUP_NAME_REGEX.matches(it) }
                val latestName = pickLatestBackupFileName(backupNames)
                    ?: return SyncResult.NoBackupFileFound
                val latestDoc = allFiles.first { it.name == latestName }

                copyIntoSnapshot(PlatformFile(latestDoc.uri))
                recordSyncedNow(database)
                SyncResult.Success
            } finally {
                directory.stopAccessingSecurityScopedResource()
            }
        } catch (e: Exception) {
            // Revoked SAF permission or any I/O failure — surfaced so the UI can prompt a reconfigure.
            SyncResult.Failed(e.message ?: "Sync failed")
        }
    }

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
        val finalFile = lokcalSnapshotFile(context)
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
