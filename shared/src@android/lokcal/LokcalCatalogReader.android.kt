package com.emilflach.groceries.lokcal

import android.content.Context
import android.database.sqlite.SQLiteDatabase

private const val FOOD_COLUMNS = "id, name, energy_kcal_per_100g, gtin13, image_url, product_url, source"

actual class LokcalCatalogReader(private val context: Context) {

    actual suspend fun hasSnapshot(): Boolean = lokcalSnapshotFile(context).exists()

    actual suspend fun browseFoods(limit: Int): List<LokcalFood> = queryFoods(
        sql = "SELECT $FOOD_COLUMNS FROM Food ORDER BY name LIMIT ?",
        args = arrayOf(limit.toString()),
    )

    actual suspend fun searchFoods(query: String): List<LokcalFood> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return queryFoods(
            sql = "SELECT $FOOD_COLUMNS FROM Food WHERE LOWER(name) LIKE ? ORDER BY name LIMIT 50",
            args = arrayOf("%${trimmed.lowercase()}%"),
        )
    }

    private fun queryFoods(sql: String, args: Array<String>): List<LokcalFood> {
        val file = lokcalSnapshotFile(context)
        if (!file.exists()) return emptyList()

        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery(sql, args).use { cursor ->
                val results = mutableListOf<LokcalFood>()
                while (cursor.moveToNext()) {
                    results += LokcalFood(
                        id = cursor.getLong(0),
                        name = cursor.getString(1),
                        energyKcalPer100g = cursor.getDouble(2),
                        gtin13 = cursor.getString(3),
                        imageUrl = cursor.getString(4),
                        productUrl = cursor.getString(5),
                        source = cursor.getString(6),
                    )
                }
                return results
            }
        } finally {
            db.close()
        }
    }
}
