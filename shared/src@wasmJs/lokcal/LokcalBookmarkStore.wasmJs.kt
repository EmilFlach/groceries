package com.emilflach.groceries.lokcal

import io.github.vinceglb.filekit.PlatformFile

actual object LokcalBookmarkStore {
    actual suspend fun save(file: PlatformFile?) {
        // SAF folder bookmarks are Android-only; no-op elsewhere.
    }

    actual suspend fun load(): PlatformFile? = null

    actual suspend fun clear() {
    }
}
