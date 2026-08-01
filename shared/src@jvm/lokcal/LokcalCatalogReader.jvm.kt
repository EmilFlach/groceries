package com.emilflach.groceries.lokcal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

actual class LokcalCatalogReader {

    // Kept open for the app's lifetime (re-opening per query was slow). Serialized by [mutex] since
    // a JDBC SQLite connection isn't concurrent-safe; reopened when the file changes (a re-import).
    private val mutex = Mutex()
    private var open: OpenSnapshot? = null

    private class OpenSnapshot(val connection: Connection, val stamp: Long, val size: Long)

    actual suspend fun hasSnapshot(): Boolean =
        withContext(Dispatchers.IO) { lokcalSnapshotFile().exists() }

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
                val connection = snapshot() ?: return@withLock default
                block(JdbcLokcalQueries(connection))
            }
        }

    /** Returns the open connection, (re)opening it if it's missing or the file changed; null if none. */
    private fun snapshot(): Connection? {
        val file = lokcalSnapshotFile()
        if (!file.exists()) {
            open?.connection?.close()
            open = null
            return null
        }
        val stamp = file.lastModified()
        val size = file.length()
        open?.let { if (!it.connection.isClosed && it.stamp == stamp && it.size == size) return it.connection }
        open?.connection?.close()
        return readOnlyConnection(file).also { open = OpenSnapshot(it, stamp, size) }
    }
}

/**
 * Opens [file] with the SQLite core itself enforcing read-only access (the JDBC equivalent
 * of Android's `SQLiteDatabase.OPEN_READONLY`), not merely a promise that this code never writes.
 */
internal fun readOnlyConnection(file: File): Connection =
    DriverManager.getConnection(
        "jdbc:sqlite:${file.path}",
        SQLiteConfig().apply { setReadOnly(true) }.toProperties(),
    )

internal class JdbcLokcalQueries(private val connection: Connection) : LokcalSnapshotQueries {

    override suspend fun browse(limit: Int): List<LokcalFood> =
        query(LokcalSearchSql.BROWSE, listOf(limit))

    override suspend fun selectByGtin13(gtin13: String): List<LokcalFood> =
        query(LokcalSearchSql.SELECT_BY_GTIN, listOf(gtin13))

    override suspend fun searchRanked(like: String, qLower: String, limit: Int): List<LokcalFood> =
        query(LokcalSearchSql.SEARCH_RANKED, listOf(like, like, qLower, qLower, limit))

    override suspend fun selectAll(): List<LokcalFood> =
        query(LokcalSearchSql.SELECT_ALL, emptyList())
    @Suppress("SqlSourceToSinkFlow")
    override suspend fun mealImages(limit: Int): List<String> =
        connection.prepareStatement(LokcalSearchSql.MEAL_IMAGES).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rs ->
                val out = ArrayList<String>()
                while (rs.next()) rs.getString(1)?.let { out += it }
                out
            }
        }

    @Suppress("SqlSourceToSinkFlow")
    override suspend fun regularFoods(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentFood> =
        connection.prepareStatement(LokcalSearchSql.REGULAR_FOODS).use { statement ->
            statement.setInt(1, windowDays)
            statement.setInt(2, minWeeks)
            statement.setInt(3, limit)
            statement.executeQuery().use { it.readFrequentFoods() }
        }

    @Suppress("SqlSourceToSinkFlow")
    override suspend fun regularMeals(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentMeal> =
        connection.prepareStatement(LokcalSearchSql.REGULAR_MEALS).use { statement ->
            statement.setInt(1, windowDays)
            statement.setInt(2, minWeeks)
            statement.setInt(3, limit)
            statement.executeQuery().use { it.readFrequentMeals() }
        }

    @Suppress("SqlSourceToSinkFlow")
    override suspend fun mealItems(mealId: Long): List<LokcalMealItem> =
        connection.prepareStatement(LokcalSearchSql.MEAL_ITEMS).use { statement ->
            statement.setLong(1, mealId)
            statement.executeQuery().use { rs ->
                val out = ArrayList<LokcalMealItem>()
                // JDBC is 1-indexed: columns 1..7 are the food (COLS_F); 8 = quantity_g.
                while (rs.next()) out += LokcalMealItem(rs.readFood(), rs.getDouble(8))
                out
            }
        }

    @Suppress("SqlSourceToSinkFlow")
    override suspend fun searchMeals(like: String, limit: Int): List<LokcalMeal> =
        connection.prepareStatement(LokcalSearchSql.SEARCH_MEALS).use { statement ->
            statement.setString(1, like)
            statement.setInt(2, limit)
            statement.executeQuery().use { rs ->
                val out = ArrayList<LokcalMeal>()
                // JDBC is 1-indexed: 1 = id, 2 = name, 3 = image_url.
                while (rs.next()) {
                    out += LokcalMeal(id = rs.getLong(1), name = rs.getString(2), imageUrl = rs.getString(3))
                }
                out
            }
        }

    private fun query(sql: String, args: List<Any>): List<LokcalFood> =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, arg ->
                when (arg) {
                    is Int -> statement.setInt(i + 1, arg)
                    is String -> statement.setString(i + 1, arg)
                    else -> statement.setObject(i + 1, arg)
                }
            }
            statement.executeQuery().use { it.readFoods() }
        }

    private fun ResultSet.readFoods(): List<LokcalFood> {
        val out = ArrayList<LokcalFood>()
        while (next()) out += readFood()
        return out
    }

    private fun ResultSet.readFrequentFoods(): List<LokcalFrequentFood> {
        val out = ArrayList<LokcalFrequentFood>()
        // JDBC is 1-indexed: columns 1..7 are the food (COLS_F); 8 = distinct weeks, 9 = last eaten.
        while (next()) out += LokcalFrequentFood(readFood(), distinctWeeks = getInt(8), lastEaten = getString(9))
        return out
    }

    private fun ResultSet.readFrequentMeals(): List<LokcalFrequentMeal> {
        val out = ArrayList<LokcalFrequentMeal>()
        // JDBC is 1-indexed: 1 = id, 2 = name, 3 = image_url, 4 = distinct weeks, 5 = last eaten.
        while (next()) {
            out += LokcalFrequentMeal(
                LokcalMeal(id = getLong(1), name = getString(2), imageUrl = getString(3)),
                distinctWeeks = getInt(4),
                lastEaten = getString(5),
            )
        }
        return out
    }

    private fun ResultSet.readFood() = LokcalFood(
        id = getLong(1),
        name = getString(2),
        energyKcalPer100g = getDouble(3),
        gtin13 = getString(4),
        imageUrl = getString(5),
        productUrl = getString(6),
        source = getString(7),
    )
}
