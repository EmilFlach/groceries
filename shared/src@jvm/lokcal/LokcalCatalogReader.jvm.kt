package com.emilflach.groceries.lokcal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

actual class LokcalCatalogReader {

    actual suspend fun hasSnapshot(): Boolean =
        withContext(Dispatchers.IO) { lokcalSnapshotFile().exists() }

    actual suspend fun browseFoods(limit: Int): List<LokcalFood> =
        read(emptyList()) { it.browse(limit) }

    actual suspend fun searchFoods(query: String): List<LokcalFood> =
        read(emptyList()) { searchCatalog(query, it) }

    actual suspend fun browseMealImages(limit: Int): List<String> =
        read(emptyList()) { it.mealImages(limit) }

    /** Opens the snapshot read-only once, runs [block] against it, and always closes it — off the main thread. */
    private suspend fun <T> read(default: T, block: suspend (LokcalSnapshotQueries) -> T): T =
        withContext(Dispatchers.IO) {
            val file = lokcalSnapshotFile()
            if (!file.exists()) return@withContext default
            readOnlyConnection(file).use { connection ->
                block(JdbcLokcalQueries(connection))
            }
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

    override suspend fun mealImages(limit: Int): List<String> =
        connection.prepareStatement(LokcalSearchSql.MEAL_IMAGES).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rs ->
                val out = ArrayList<String>()
                while (rs.next()) rs.getString(1)?.let { out += it }
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
        while (next()) {
            out += LokcalFood(
                id = getLong(1),
                name = getString(2),
                energyKcalPer100g = getDouble(3),
                gtin13 = getString(4),
                imageUrl = getString(5),
                productUrl = getString(6),
                source = getString(7),
            )
        }
        return out
    }
}
