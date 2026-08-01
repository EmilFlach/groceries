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

    actual suspend fun frequentFoods(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentFood> =
        read(emptyList()) { it.regularFoods(windowDays, minWeeks, limit) }

    actual suspend fun frequentMeals(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentMeal> =
        read(emptyList()) { it.regularMeals(windowDays, minWeeks, limit) }

    actual suspend fun mealItems(mealId: Long): List<LokcalMealItem> =
        read(emptyList()) { it.mealItems(mealId) }

    actual suspend fun searchMeals(query: String): List<LokcalMeal> =
        read(emptyList()) { searchMeals(query, it) }

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

    override suspend fun regularFoods(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentFood> =
        db.rawQuery(
            LokcalSearchSql.REGULAR_FOODS,
            arrayOf(windowDays.toString(), minWeeks.toString(), limit.toString()),
        ).use { it.readFrequentFoods() }

    override suspend fun regularMeals(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentMeal> =
        db.rawQuery(
            LokcalSearchSql.REGULAR_MEALS,
            arrayOf(windowDays.toString(), minWeeks.toString(), limit.toString()),
        ).use { it.readFrequentMeals() }

    override suspend fun mealItems(mealId: Long): List<LokcalMealItem> =
        db.rawQuery(LokcalSearchSql.MEAL_ITEMS, arrayOf(mealId.toString())).use { cursor ->
            val out = ArrayList<LokcalMealItem>(cursor.count)
            // Columns 0..6 are the food (COLS_F); 7 = quantity_g (the trailing MEAL_ITEMS column).
            while (cursor.moveToNext()) out += LokcalMealItem(cursor.readFood(), cursor.getDouble(7))
            out
        }

    override suspend fun searchMeals(like: String, limit: Int): List<LokcalMeal> =
        db.rawQuery(LokcalSearchSql.SEARCH_MEALS, arrayOf(like, limit.toString())).use { cursor ->
            val out = ArrayList<LokcalMeal>(cursor.count)
            // Columns 0..2 = id, name, image_url.
            while (cursor.moveToNext()) {
                out += LokcalMeal(id = cursor.getLong(0), name = cursor.getString(1), imageUrl = cursor.getString(2))
            }
            out
        }

    private fun query(sql: String, args: Array<String>?): List<LokcalFood> =
        db.rawQuery(sql, args).use { it.readFoods() }

    private fun Cursor.readFoods(): List<LokcalFood> {
        val out = ArrayList<LokcalFood>(count)
        while (moveToNext()) out += readFood()
        return out
    }

    private fun Cursor.readFrequentFoods(): List<LokcalFrequentFood> {
        val out = ArrayList<LokcalFrequentFood>(count)
        while (moveToNext()) {
            // Columns 0..6 are the food (COLS_F); 7 = distinct weeks, 8 = last eaten.
            out += LokcalFrequentFood(readFood(), distinctWeeks = getInt(7), lastEaten = getString(8))
        }
        return out
    }

    private fun Cursor.readFrequentMeals(): List<LokcalFrequentMeal> {
        val out = ArrayList<LokcalFrequentMeal>(count)
        while (moveToNext()) {
            // Columns 0 = id, 1 = name, 2 = image_url, 3 = distinct weeks, 4 = last eaten.
            out += LokcalFrequentMeal(
                LokcalMeal(id = getLong(0), name = getString(1), imageUrl = getString(2)),
                distinctWeeks = getInt(3),
                lastEaten = getString(4),
            )
        }
        return out
    }

    private fun Cursor.readFood() = LokcalFood(
        id = getLong(0),
        name = getString(1),
        energyKcalPer100g = getDouble(2),
        gtin13 = getString(3),
        imageUrl = getString(4),
        productUrl = getString(5),
        source = getString(6),
    )
}
