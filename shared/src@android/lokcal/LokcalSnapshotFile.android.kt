package com.emilflach.groceries.lokcal

import android.content.Context
import java.io.File

/** Where the locally-copied read-only snapshot of Lokcal's database lives on this device. */
internal fun lokcalSnapshotFile(context: Context): File =
    File(context.filesDir, "lokcal_import/lokcal_snapshot.db")
