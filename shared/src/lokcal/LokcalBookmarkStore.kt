package com.emilflach.groceries.lokcal

import io.github.vinceglb.filekit.PlatformFile

/**
 * Persists Groceries' own SAF permission grant on the shared Lokcal-backup folder.
 *
 * This is intentionally separate from Lokcal's own bookmark storage: Android's SAF
 * persistable-URI permissions are scoped per requesting app, so Groceries must obtain
 * and store its own grant even when pointed at the exact folder Lokcal already uses.
 */
expect object LokcalBookmarkStore {
    suspend fun save(file: PlatformFile?)
    suspend fun load(): PlatformFile?
    suspend fun clear()
}
