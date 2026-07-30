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
 * The meal-level twin of [RegularFoodsQueryTest]: exercises the "cooked regularly" query
 * ([JdbcLokcalQueries.regularMeals] over [LokcalSearchSql.REGULAR_MEALS]) and the ingredient lookup
 * ([JdbcLokcalQueries.mealItems] over [LokcalSearchSql.MEAL_ITEMS]) against a synthetic in-memory
 * SQLite snapshot. MEAL intakes are inserted at controlled ages so distinct-week counting, the
 * window bound, the source filter and ordering are all checked deterministically.
 */
class RegularMealsQueryTest {
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
            st.executeUpdate("CREATE TABLE Meal (id INTEGER PRIMARY KEY, name TEXT NOT NULL, image_url TEXT)")
            st.executeUpdate(
                "CREATE TABLE MealItem (id INTEGER PRIMARY KEY AUTOINCREMENT, meal_id INTEGER NOT NULL, " +
                    "food_id INTEGER NOT NULL, quantity_g REAL NOT NULL DEFAULT 0)",
            )
            st.executeUpdate(
                "CREATE TABLE Intake (id INTEGER PRIMARY KEY AUTOINCREMENT, source_type TEXT NOT NULL, " +
                    "source_food_id INTEGER, source_meal_id INTEGER, timestamp TEXT NOT NULL)",
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

    private fun meal(id: Long, name: String) {
        //noinspection SqlResolve
        connection.prepareStatement("INSERT INTO Meal(id, name) VALUES (?, ?)").use {
            it.setLong(1, id); it.setString(2, name); it.executeUpdate()
        }
    }

    @Suppress("SameParameterValue") // general helper; the current tests happen to only use meal 1
    private fun mealItem(mealId: Long, foodId: Long) {
        //noinspection SqlResolve
        connection.prepareStatement("INSERT INTO MealItem(meal_id, food_id) VALUES (?, ?)").use {
            it.setLong(1, mealId); it.setLong(2, foodId); it.executeUpdate()
        }
    }

    /** Logs a meal intake [daysAgo] days before now; [type] 'MEAL' unless overridden. */
    private fun mealIntake(mealId: Long, daysAgo: Int, type: String = "MEAL") {
        //noinspection SqlResolve
        connection.prepareStatement(
            "INSERT INTO Intake(source_type, source_meal_id, timestamp) VALUES (?, ?, date('now', ?))",
        ).use {
            it.setString(1, type); it.setLong(2, mealId); it.setString(3, "-$daysAgo days"); it.executeUpdate()
        }
    }

    @Test
    fun countsDistinctWeeksNotRawLogs() = runTest {
        meal(1, "Pasta")
        // Three logs on the same day → one distinct week.
        mealIntake(1, 1); mealIntake(1, 1); mealIntake(1, 1)
        meal(2, "Curry")
        // Three logs a week apart → three distinct weeks.
        mealIntake(2, 1); mealIntake(2, 8); mealIntake(2, 15)

        val byName = queries.regularMeals(windowDays = 60, minWeeks = 1, limit = 10)
            .associate { it.meal.name to it.distinctWeeks }

        assertEquals(1, byName["Pasta"])
        assertEquals(3, byName["Curry"])
    }

    @Test
    fun thresholdFiltersByMinWeeks() = runTest {
        meal(1, "Pasta"); mealIntake(1, 1) // 1 week
        meal(2, "Curry"); mealIntake(2, 1); mealIntake(2, 8); mealIntake(2, 15) // 3 weeks

        val names = queries.regularMeals(windowDays = 60, minWeeks = 3, limit = 10).map { it.meal.name }

        assertEquals(listOf("Curry"), names)
    }

    @Test
    fun windowExcludesOldIntakes() = runTest {
        meal(1, "Roast")
        // All logs land outside an 84-day window, even though they span three distinct weeks.
        mealIntake(1, 100); mealIntake(1, 107); mealIntake(1, 114)

        assertTrue(queries.regularMeals(windowDays = 84, minWeeks = 1, limit = 10).isEmpty())
    }

    @Test
    fun ignoresNonMealSources() = runTest {
        meal(1, "Stew")
        mealIntake(1, 1, type = "FOOD"); mealIntake(1, 8, type = "FOOD")

        assertTrue(queries.regularMeals(windowDays = 60, minWeeks = 1, limit = 10).isEmpty())
    }

    @Test
    fun ordersByWeeksDescending() = runTest {
        meal(1, "Pasta"); mealIntake(1, 1); mealIntake(1, 8) // 2 weeks
        meal(2, "Curry"); mealIntake(2, 1); mealIntake(2, 8); mealIntake(2, 15) // 3 weeks
        meal(3, "Soup"); mealIntake(3, 1) // 1 week

        val names = queries.regularMeals(windowDays = 60, minWeeks = 1, limit = 10).map { it.meal.name }

        assertEquals(listOf("Curry", "Pasta", "Soup"), names)
    }

    @Test
    fun respectsLimit() = runTest {
        meal(1, "Pasta"); mealIntake(1, 1)
        meal(2, "Curry"); mealIntake(2, 1)
        meal(3, "Soup"); mealIntake(3, 1)

        assertEquals(2, queries.regularMeals(windowDays = 60, minWeeks = 1, limit = 2).size)
    }

    @Test
    @Suppress("SqlSourceToSinkFlow") // constant query text from LokcalSearchSql; not user input
    fun thresholdHoldsWhenParamsBoundAsText() = runTest {
        // Android's SQLiteDatabase.rawQuery binds every arg as TEXT; without the CAST in the SQL,
        // `weeks >= '3'` compares an INTEGER count against TEXT (always false) and returns nothing —
        // the bug behind "no meals suggested on device". Bind the params as strings (as Android
        // does) and confirm the threshold still filters correctly.
        meal(1, "Pasta"); mealIntake(1, 1) // 1 week
        meal(2, "Curry"); mealIntake(2, 1); mealIntake(2, 8); mealIntake(2, 15) // 3 weeks

        val names = ArrayList<String>()
        connection.prepareStatement(LokcalSearchSql.REGULAR_MEALS).use { st ->
            st.setString(1, "60"); st.setString(2, "3"); st.setString(3, "10")
            st.executeQuery().use { rs -> while (rs.next()) names += rs.getString(2) }
        }

        assertEquals(listOf("Curry"), names)
    }

    @Test
    fun mealItemsReturnsIngredientsInItemOrder() = runTest {
        meal(1, "Pasta")
        food(10, "Spaghetti"); food(11, "Tomato"); food(12, "Beef")
        // Inserted out of food-id order; MEAL_ITEMS orders by MealItem.id, i.e. insertion order.
        mealItem(1, 12); mealItem(1, 10); mealItem(1, 11)

        val names = queries.mealItems(1).map { it.name }

        assertEquals(listOf("Beef", "Spaghetti", "Tomato"), names)
    }
}
