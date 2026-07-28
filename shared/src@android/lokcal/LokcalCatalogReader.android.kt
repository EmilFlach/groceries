package com.emilflach.groceries.lokcal

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

actual class LokcalCatalogReader(private val context: Context) {

    // Kept open for the app's lifetime (re-opening per query was slow). Serialized by [mutex], which
    // guards the cache; reopened when the file changes underneath us (a re-import).
    private val mutex = Mutex()
    private var open: OpenSnapshot? = null

    private class OpenSnapshot(val db: SQLiteDatabase, val stamp: Long, val size: Long)

    actual suspend fun hasSnapshot(): Boolean =
        withContext(Dispatchers.IO) { lokcalSnapshotFile(context).exists() }

    actual suspend fun browseFoods(limit: Int): List<LokcalFood> =
        read(emptyList()) { it.browse(limit) }

    actual suspend fun searchFoods(query: String): List<LokcalFood> =
        read(emptyList()) { searchCatalog(query, it) }

    actual suspend fun browseMealImages(limit: Int): List<String> =
        read(emptyList()) { it.mealImages(limit) }

    /** Runs [block] against the cached read-only snapshot — off the main thread and one at a time. */
    private suspend fun <T> read(default: T, block: suspend (LokcalSnapshotQueries) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = snapshot() ?: return@withLock default
                block(AndroidLokcalQueries(db))
            }
        }

    /** Returns the open snapshot, (re)opening it if it's missing or the file changed; null if none. */
    private fun snapshot(): SQLiteDatabase? {
        val file = lokcalSnapshotFile(context)
        if (!file.exists()) {
            open?.db?.close()
            open = null
            return null
        }
        val stamp = file.lastModified()
        val size = file.length()
        open?.let { if (it.db.isOpen && it.stamp == stamp && it.size == size) return it.db }
        open?.db?.close()
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            .also { open = OpenSnapshot(it, stamp, size) }
    }
}

private class AndroidLokcalQueries(private val db: SQLiteDatabase) : LokcalSnapshotQueries {

    override suspend fun browse(limit: Int): List<LokcalFood> =
        query(LokcalSearchSql.BROWSE, arrayOf(limit.toString()))

    override suspend fun selectByGtin13(gtin13: String): List<LokcalFood> =
        query(LokcalSearchSql.SELECT_BY_GTIN, arrayOf(gtin13))

    override suspend fun searchRanked(like: String, qLower: String, limit: Int): List<LokcalFood> =
        query(LokcalSearchSql.SEARCH_RANKED, arrayOf(like, like, qLower, qLower, limit.toString()))

    override suspend fun selectAll(): List<LokcalFood> =
        query(LokcalSearchSql.SELECT_ALL, null)

    override suspend fun mealImages(limit: Int): List<String> =
        db.rawQuery(LokcalSearchSql.MEAL_IMAGES, arrayOf(limit.toString())).use { cursor ->
            val out = ArrayList<String>(cursor.count)
            while (cursor.moveToNext()) cursor.getString(0)?.let { out += it }
            out
        }

    private fun query(sql: String, args: Array<String>?): List<LokcalFood> =
        db.rawQuery(sql, args).use { it.readFoods() }

    private fun Cursor.readFoods(): List<LokcalFood> {
        val out = ArrayList<LokcalFood>(count)
        while (moveToNext()) {
            out += LokcalFood(
                id = getLong(0),
                name = getString(1),
                energyKcalPer100g = getDouble(2),
                gtin13 = getString(3),
                imageUrl = getString(4),
                productUrl = getString(5),
                source = getString(6),
            )
        }
        return out
    }
}
