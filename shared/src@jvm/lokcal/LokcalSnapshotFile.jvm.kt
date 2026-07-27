package com.emilflach.groceries.lokcal

import java.io.File

/**
 * Where the locally-copied read-only snapshot of Lokcal's database lives on this device.
 *
 * This JVM driver is used only by the desktop app (`./kotlin run -m desktopApp`, which runs
 * from the repo root), so keep it under desktopApp/, next to its dev `groceries.db`
 * (see `SqlDriverFactory.jvm.kt`).
 */
internal fun lokcalSnapshotFile(): File = File("desktopApp/lokcal_snapshot.db")
