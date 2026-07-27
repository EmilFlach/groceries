package com.emilflach.groceries.lokcal

private val BACKUP_FILE_REGEX = Regex("""^lokcal-backup-(\d+)\.db$""")

/**
 * Picks the most recent Lokcal backup from a folder listing, using the epoch-millis
 * timestamp embedded in the filename by Lokcal's own nightly export
 * (`lokcal-backup-<epoch-millis>.db` — see BackupManager.android.kt in the Lokcal repo).
 * That export has no pruning, so a folder can contain many backups; this always picks
 * the newest one rather than assuming a single/fixed filename.
 */
fun pickLatestBackupFileName(names: List<String>): String? {
    return names
        .mapNotNull { name -> BACKUP_FILE_REGEX.matchEntire(name)?.let { name to it.groupValues[1].toLong() } }
        .maxByOrNull { (_, timestamp) -> timestamp }
        ?.first
}
