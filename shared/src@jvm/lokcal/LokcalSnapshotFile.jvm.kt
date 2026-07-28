package com.emilflach.groceries.lokcal

import java.io.File

/** Desktop-only snapshot path, under desktopApp/ next to the dev `groceries.db` (repo root is the cwd). */
internal fun lokcalSnapshotFile(): File = File("desktopApp/lokcal_snapshot.db")
