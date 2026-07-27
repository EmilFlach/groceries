@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.emilflach.groceries.lokcal

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write

actual object LokcalBookmarkStore {
    private val bookmarkFile = FileKit.filesDir / "lokcal_folder_bookmark.bin"

    actual suspend fun save(file: PlatformFile?) {
        try {
            val bookmark = file!!.bookmarkData()
            bookmarkFile.write(bookmark.bytes)
        } catch (_: Exception) {
            // Error saving bookmark
        }
    }

    actual suspend fun load(): PlatformFile? {
        if (!bookmarkFile.exists()) return null

        return try {
            val bytes = bookmarkFile.readBytes()
            val file = PlatformFile.fromBookmarkData(bytes)

            if (file.exists()) {
                file
            } else {
                clear()
                null
            }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    actual suspend fun clear() {
        try {
            if (bookmarkFile.exists()) {
                bookmarkFile.delete()
            }
        } catch (_: Exception) {
            // Error clearing bookmark
        }
    }
}
