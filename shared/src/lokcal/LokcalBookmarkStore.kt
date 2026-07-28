package com.emilflach.groceries.lokcal

import io.github.vinceglb.filekit.PlatformFile

/**
 * Persists Groceries' own SAF grant on the shared Lokcal-backup folder — separate from Lokcal's,
 * since Android's persistable-URI permissions are scoped per app even for the same folder.
 */
expect object LokcalBookmarkStore {
    suspend fun save(file: PlatformFile?)
    suspend fun load(): PlatformFile?
    suspend fun clear()
}
