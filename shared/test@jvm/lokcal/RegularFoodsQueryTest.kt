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
 * Exercises the "regularly bought" query ([JdbcLokcalQueries.regularFoods] over
 * [LokcalSearchSql.REGULAR_FOODS]) against a synthetic in-memory SQLite snapshot. Intake rows are
 * inserted at controlled ages so the distinct-week counting, window bound, source filter and
 * ordering are all checked deterministically.
 */
class RegularFoodsQueryTest {
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
                "CREATE TABLE Intake (id INTEGER PRIMARY KEY AUTOINCREMENT, source_type TEXT NOT NULL, " +
                    "source_food_id INTEGER, timestamp TEXT NOT NULL)",
            )
        }
        queries = JdbcLokcalQueries(connection)
    }

    @AfterTest
    fun teardown() = connection.close()

    private fun food(id: Long, name: String) {
        // Synthetic Lokcal tables live only in this in-memory DB, so the IDE's SQL resolver can't see them.
        //noinspection SqlResolve
        connection.prepareStatement("INSERT INTO Food(id, name) VALUES (?, ?)").use {
            it.setLong(1, id); it.setString(2, name); it.executeUpdate()
        }
    }

    /** Logs an intake [daysAgo] days before now; [type] 'FOOD' unless overridden. */
    private fun intake(foodId: Long, daysAgo: Int, type: String = "FOOD") {
        //noinspection SqlResolve
        connection.prepareStatement(
            "INSERT INTO Intake(source_type, source_food_id, timestamp) VALUES (?, ?, date('now', ?))",
        ).use {
            it.setString(1, type); it.setLong(2, foodId); it.setString(3, "-$daysAgo days"); it.executeUpdate()
        }
    }

    @Test
    fun countsDistinctWeeksNotRawLogs() = runTest {
        food(1, "Milk")
        // Three logs on the same day → one distinct week.
        intake(1, 1); intake(1, 1); intake(1, 1)
        food(2, "Eggs")
        // Three logs a week apart → three distinct weeks.
        intake(2, 1); intake(2, 8); intake(2, 15)

        val byName = queries.regularFoods(windowDays = 60, minWeeks = 1, limit = 10)
            .associate { it.food.name to it.distinctWeeks }

        assertEquals(1, byName["Milk"])
        assertEquals(3, byName["Eggs"])
    }

    @Test
    fun thresholdFiltersByMinWeeks() = runTest {
        food(1, "Milk"); intake(1, 1) // 1 week
        food(2, "Eggs"); intake(2, 1); intake(2, 8); intake(2, 15) // 3 weeks

        val names = queries.regularFoods(windowDays = 60, minWeeks = 3, limit = 10).map { it.food.name }

        assertEquals(listOf("Eggs"), names)
    }

    @Test
    fun windowExcludesOldIntakes() = runTest {
        food(1, "Bread")
        // All logs land outside an 84-day window, even though they span three distinct weeks.
        intake(1, 100); intake(1, 107); intake(1, 114)

        assertTrue(queries.regularFoods(windowDays = 84, minWeeks = 1, limit = 10).isEmpty())
    }

    @Test
    fun ignoresNonFoodSources() = runTest {
        food(1, "Rice")
        intake(1, 1, type = "MEAL"); intake(1, 8, type = "MEAL")

        assertTrue(queries.regularFoods(windowDays = 60, minWeeks = 1, limit = 10).isEmpty())
    }

    @Test
    fun ordersByWeeksDescending() = runTest {
        food(1, "Milk"); intake(1, 1); intake(1, 8) // 2 weeks
        food(2, "Eggs"); intake(2, 1); intake(2, 8); intake(2, 15) // 3 weeks
        food(3, "Butter"); intake(3, 1) // 1 week

        val names = queries.regularFoods(windowDays = 60, minWeeks = 1, limit = 10).map { it.food.name }

        assertEquals(listOf("Eggs", "Milk", "Butter"), names)
    }

    @Test
    fun respectsLimit() = runTest {
        food(1, "Milk"); intake(1, 1)
        food(2, "Eggs"); intake(2, 1)
        food(3, "Butter"); intake(3, 1)

        assertEquals(2, queries.regularFoods(windowDays = 60, minWeeks = 1, limit = 2).size)
    }

    @Test
    @Suppress("SqlSourceToSinkFlow") // constant query text from LokcalSearchSql; not user input
    fun thresholdHoldsWhenParamsBoundAsText() = runTest {
        // Android's SQLiteDatabase.rawQuery binds every arg as TEXT. Without the CAST in the SQL,
        // `weeks >= '3'` compares an INTEGER count against TEXT and is always false, so nothing
        // comes back — the bug behind "no suggestions on device". Run the raw SQL with string-bound
        // params (what Android does) and confirm the threshold still filters correctly.
        food(1, "Milk"); intake(1, 1) // 1 week
        food(2, "Eggs"); intake(2, 1); intake(2, 8); intake(2, 15) // 3 weeks

        val names = ArrayList<String>()
        connection.prepareStatement(LokcalSearchSql.REGULAR_FOODS).use { st ->
            st.setString(1, "60"); st.setString(2, "3"); st.setString(3, "10")
            st.executeQuery().use { rs -> while (rs.next()) names += rs.getString(2) }
        }

        assertEquals(listOf("Eggs"), names)
    }
}
