package com.emilflach.groceries.lokcal

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.DriverManager
import java.sql.PreparedStatement

private const val FOOD_COLUMNS = "id, name, energy_kcal_per_100g, gtin13, image_url, product_url, source"

actual class LokcalCatalogReader {

    actual suspend fun hasSnapshot(): Boolean = lokcalSnapshotFile().exists()

    actual suspend fun browseFoods(limit: Int): List<LokcalFood> = queryFoods(
        sql = "SELECT $FOOD_COLUMNS FROM Food ORDER BY name LIMIT ?",
    ) { it.setInt(1, limit) }

    actual suspend fun searchFoods(query: String): List<LokcalFood> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return queryFoods(
            sql = "SELECT $FOOD_COLUMNS FROM Food WHERE LOWER(name) LIKE ? ORDER BY name LIMIT 50",
        ) { it.setString(1, "%${trimmed.lowercase()}%") }
    }

    private fun queryFoods(sql: String, bind: (PreparedStatement) -> Unit): List<LokcalFood> {
        val file = lokcalSnapshotFile()
        if (!file.exists()) return emptyList()

        return readOnlyConnection(file).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement)
                statement.executeQuery().use { resultSet ->
                    val results = mutableListOf<LokcalFood>()
                    while (resultSet.next()) {
                        results += LokcalFood(
                            id = resultSet.getLong(1),
                            name = resultSet.getString(2),
                            energyKcalPer100g = resultSet.getDouble(3),
                            gtin13 = resultSet.getString(4),
                            imageUrl = resultSet.getString(5),
                            productUrl = resultSet.getString(6),
                            source = resultSet.getString(7),
                        )
                    }
                    results
                }
            }
        }
    }

    /**
     * Opens [file] with the SQLite core itself enforcing read-only access (the JDBC equivalent
     * of Android's `SQLiteDatabase.OPEN_READONLY`), not merely a promise that this class never
     * issues a write statement.
     */
    private fun readOnlyConnection(file: File) =
        DriverManager.getConnection(
            "jdbc:sqlite:${file.path}",
            SQLiteConfig().apply { setReadOnly(true) }.toProperties(),
        )
}
