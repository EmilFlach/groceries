package com.emilflach.groceries.lokcal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupFileNamingTest {
    @Test
    fun testPicksNewestByEmbeddedTimestamp() {
        val names = listOf(
            "lokcal-backup-1000.db",
            "lokcal-backup-3000.db",
            "lokcal-backup-2000.db",
        )
        assertEquals("lokcal-backup-3000.db", pickLatestBackupFileName(names))
    }

    @Test
    fun testIgnoresNonMatchingFiles() {
        val names = listOf(
            "lokcal-backup-1000.db",
            "readme.txt",
            "lokcal-backup-corrupted.db",
        )
        assertEquals("lokcal-backup-1000.db", pickLatestBackupFileName(names))
    }

    @Test
    fun testEmptyListReturnsNull() {
        assertNull(pickLatestBackupFileName(emptyList()))
    }

    @Test
    fun testNoMatchesReturnsNull() {
        assertNull(pickLatestBackupFileName(listOf("readme.txt", "other.db")))
    }

    @Test
    fun testSingleFile() {
        assertEquals("lokcal-backup-42.db", pickLatestBackupFileName(listOf("lokcal-backup-42.db")))
    }
}
