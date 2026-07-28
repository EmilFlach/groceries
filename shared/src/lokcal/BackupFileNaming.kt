package com.emilflach.groceries.lokcal

private val BACKUP_FILE_REGEX = Regex("""^lokcal-backup-(\d+)\.db$""")

/**
 * Picks the newest Lokcal backup from a folder listing by the epoch-millis in its filename
 * (`lokcal-backup-<epoch-millis>.db`). Lokcal's export doesn't prune, so many can accumulate.
 */
fun pickLatestBackupFileName(names: List<String>): String? {
    return names
        .mapNotNull { name -> BACKUP_FILE_REGEX.matchEntire(name)?.let { name to it.groupValues[1].toLong() } }
        .maxByOrNull { (_, timestamp) -> timestamp }
        ?.first
}
