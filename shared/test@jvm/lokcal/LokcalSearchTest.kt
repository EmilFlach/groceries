package com.emilflach.groceries.lokcal

import kotlinx.coroutines.test.runTest
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the ranked search cascade ([searchCatalog]) ported from Lokcal against a synthetic
 * in-memory SQLite snapshot, through the real JDBC query implementation ([JdbcLokcalQueries]).
 */
class LokcalSearchTest {
    private lateinit var connection: Connection
    private lateinit var queries: JdbcLokcalQueries

    @BeforeTest
    fun setup() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { st ->
            st.executeUpdate(
                "CREATE TABLE Food (id INTEGER PRIMARY KEY, name TEXT NOT NULL, " +
                    "energy_kcal_per_100g REAL NOT NULL DEFAULT 0, gtin13 TEXT, image_url TEXT, " +
                    "product_url TEXT, source TEXT)",
            )
            st.executeUpdate(
                "CREATE TABLE FoodAlias (id INTEGER PRIMARY KEY, food_id INTEGER NOT NULL, " +
                    "alias TEXT NOT NULL, alias_type TEXT NOT NULL DEFAULT 'SYNONYM')",
            )
            st.executeUpdate(
                "CREATE TABLE Intake (id INTEGER PRIMARY KEY, source_type TEXT NOT NULL, source_food_id INTEGER)",
            )
            st.executeUpdate(
                "CREATE TABLE Meal (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, image_url TEXT)",
            )
        }
        queries = JdbcLokcalQueries(connection)
    }

    private fun meal(name: String, imageUrl: String?) {
        connection.prepareStatement("INSERT INTO Meal(name, image_url) VALUES (?, ?)").use {
            it.setString(1, name); it.setString(2, imageUrl); it.executeUpdate()
        }
    }

    @AfterTest
    fun teardown() = connection.close()

    private fun food(id: Long, name: String, gtin13: String? = null) {
        connection.prepareStatement("INSERT INTO Food(id, name, gtin13) VALUES (?, ?, ?)").use {
            it.setLong(1, id); it.setString(2, name); it.setString(3, gtin13); it.executeUpdate()
        }
    }

    private fun alias(foodId: Long, alias: String) {
        connection.prepareStatement("INSERT INTO FoodAlias(food_id, alias) VALUES (?, ?)").use {
            it.setLong(1, foodId); it.setString(2, alias); it.executeUpdate()
        }
    }

    private fun logged(foodId: Long, times: Int) {
        repeat(times) {
            connection.prepareStatement("INSERT INTO Intake(source_type, source_food_id) VALUES ('FOOD', ?)").use {
                it.setLong(1, foodId); it.executeUpdate()
            }
        }
    }

    @Test
    fun mealImagesReturnsNewestFirstSkippingBlanks() = runTest {
        meal("Oatmeal", "https://img/oats.jpg")       // id 1
        meal("Salad", null)                             // no photo — skipped
        meal("Curry", "   ")                            // blank photo — skipped
        meal("Stir fry", "https://img/stirfry.jpg")     // id 4
        val images = queries.mealImages(10)
        assertEquals(listOf("https://img/stirfry.jpg", "https://img/oats.jpg"), images)
    }

    @Test
    fun mealImagesRespectsLimit() = runTest {
        meal("A", "https://img/a.jpg")
        meal("B", "https://img/b.jpg")
        meal("C", "https://img/c.jpg")
        assertEquals(2, queries.mealImages(2).size)
    }

    @Test
    fun exactMatchOutranksPrefixAndSubstring() = runTest {
        food(1, "Melk"); logged(1, 1)
        food(2, "Melkchocolade")
        food(3, "Karnemelk")
        val names = searchCatalog("melk", queries).map { it.name }
        assertEquals("Melk", names.first(), "exact name match (score 20) must rank first")
        assertTrue(names.indexOf("Melkchocolade") < names.indexOf("Karnemelk"), "prefix must outrank substring")
    }

    @Test
    fun popularityBreaksTiesWithinSameMatchTier() = runTest {
        food(1, "Apple"); logged(1, 1)
        food(2, "Apricot"); logged(2, 9)
        // Both are prefix matches (score 10); track count decides order.
        val names = searchCatalog("ap", queries).map { it.name }
        assertEquals(listOf("Apricot", "Apple"), names)
    }

    @Test
    fun matchesOnAlias() = runTest {
        food(1, "Banana")
        alias(1, "banaan")
        val names = searchCatalog("banaan", queries).map { it.name }
        assertEquals(listOf("Banana"), names)
    }

    @Test
    fun accentFoldingFallback() = runTest {
        food(1, "Crème fraîche")
        // SQLite LIKE can't match the accents, so this exercises the normalized full-scan fallback.
        val names = searchCatalog("creme", queries).map { it.name }
        assertEquals(listOf("Crème fraîche"), names)
    }

    @Test
    fun ampersandAndApostropheNormalization() = runTest {
        food(1, "Ben & Jerry's")
        assertEquals(listOf("Ben & Jerry's"), searchCatalog("ben and jerrys", queries).map { it.name })
    }

    @Test
    fun levenshteinTypoFallback() = runTest {
        food(1, "Banana")
        val names = searchCatalog("bananna", queries).map { it.name }
        assertEquals(listOf("Banana"), names)
    }

    @Test
    fun barcodeLookupTakesPriority() = runTest {
        food(1, "Cola", gtin13 = "8710398526937")
        food(2, "Not a barcode match")
        val names = searchCatalog("8710398526937", queries).map { it.name }
        assertEquals(listOf("Cola"), names)
    }

    @Test
    fun multiWordRequiresAllTokens() = runTest {
        food(1, "Volle melk")
        food(2, "Halfvolle yoghurt")
        // "volle" is the longest token → SQL candidates, then both tokens must be present.
        assertEquals(listOf("Volle melk"), searchCatalog("volle melk", queries).map { it.name })
    }

    @Test
    fun browseOrdersByPopularity() = runTest {
        food(1, "Zucchini"); logged(1, 5)
        food(2, "Apple"); logged(2, 1)
        food(3, "Banana"); logged(3, 10)
        assertEquals(listOf("Banana", "Zucchini", "Apple"), queries.browse(10).map { it.name })
    }

    @Test
    fun searchMealsMatchesNameSubstringCaseInsensitiveAndNameOrdered() = runTest {
        meal("Chicken Curry", "https://img/curry.jpg")
        meal("Beef Stew", null)
        meal("Chickpea Salad", null)
        val hits = searchMeals("CHICK", queries)
        assertEquals(listOf("Chicken Curry", "Chickpea Salad"), hits.map { it.name })
        // Image url rides along for the ones that have it.
        assertEquals("https://img/curry.jpg", hits.first().imageUrl)
    }

    @Test
    fun searchMealsBlankOrNoMatchIsEmpty() = runTest {
        meal("Chicken Curry", null)
        assertTrue(searchMeals("   ", queries).isEmpty())
        assertTrue(searchMeals("sushi", queries).isEmpty())
    }
}
