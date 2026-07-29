package com.emilflach.groceries.lokcal

/**
 * Platform-agnostic relevance search over the imported Lokcal snapshot, ported from Lokcal's own
 * `FoodRepository.search`. SQL does the ranking ([LokcalSearchSql.SEARCH_RANKED]); this file holds
 * the Kotlin cascade around it (barcode shortcut, token filter, accent/typo fallbacks), testable on JVM.
 */

internal const val SEARCH_LIMIT = 100

/** Raw, read-only access to an opened Lokcal snapshot. */
internal interface LokcalSnapshotQueries {
    /** Foods ordered by popularity (Intake track count) then name — the default browse ordering. */
    suspend fun browse(limit: Int): List<LokcalFood>

    suspend fun selectByGtin13(gtin13: String): List<LokcalFood>

    /**
     * Foods whose name OR any alias matches [like], ranked by exact(20) > prefix(10) > substring(0)
     * on the name plus track count, tie-broken by name, capped at [limit].
     */
    suspend fun searchRanked(like: String, qLower: String, limit: Int): List<LokcalFood>

    /** Every food (unranked) — only used by the accent/typo fallbacks. */
    suspend fun selectAll(): List<LokcalFood>

    /** Image URLs of meals that carry a photo, newest first, capped at [limit]. */
    suspend fun mealImages(limit: Int): List<String>

    /**
     * Foods logged in Intake as a FOOD source within the last [windowDays], grouped by food and
     * kept only if they appear in at least [minWeeks] distinct calendar weeks — the "regularly
     * bought" signal. Ordered most-weeks-first, capped at [limit].
     */
    suspend fun regularFoods(windowDays: Int, minWeeks: Int, limit: Int): List<LokcalFrequentFood>
}

/** SQL shared by the Android (`SQLiteDatabase`) and JVM (JDBC) actuals — both plain SQLite. */
internal object LokcalSearchSql {
    private const val COLS = "id, name, energy_kcal_per_100g, gtin13, image_url, product_url, source"
    private const val COLS_F = "f.id, f.name, f.energy_kcal_per_100g, f.gtin13, f.image_url, f.product_url, f.source"

    // Popularity = number of times a food was logged in Intake as a FOOD source.
    private const val TRACK_JOIN =
        "LEFT JOIN (SELECT source_food_id, COUNT(*) AS track_count FROM Intake " +
            "WHERE source_type = 'FOOD' AND source_food_id IS NOT NULL GROUP BY source_food_id) tc " +
            "ON tc.source_food_id = f.id"

    const val SELECT_BY_GTIN = "SELECT $COLS FROM Food WHERE gtin13 = ?"
    const val SELECT_ALL = "SELECT $COLS FROM Food"

    // Meal photos, newest first (id is autoincrement), skipping meals without a usable image.
    const val MEAL_IMAGES =
        "SELECT image_url FROM Meal WHERE image_url IS NOT NULL AND TRIM(image_url) != '' ORDER BY id DESC LIMIT ?"

    const val BROWSE =
        "SELECT $COLS_F FROM Food f $TRACK_JOIN ORDER BY COALESCE(tc.track_count, 0) DESC, f.name LIMIT ?"

    // Foods eaten across many recent weeks = the "regularly bought" signal. strftime('%Y-%W', …)
    // buckets each intake by year + week-of-year (avoiding %G/%V, which older SQLite lacks) so two
    // logs in the same week count once. date('now','-N days') bounds the window.
    // Params, in order: windowDays, minWeeks, limit.
    const val REGULAR_FOODS =
        "SELECT $COLS_F, " +
            "COUNT(DISTINCT strftime('%Y-%W', i.timestamp)) AS weeks, MAX(i.timestamp) AS last_eaten " +
            "FROM Intake i JOIN Food f ON f.id = i.source_food_id " +
            "WHERE i.source_type = 'FOOD' AND i.source_food_id IS NOT NULL " +
            "AND i.timestamp >= date('now', '-' || ? || ' days') " +
            "GROUP BY i.source_food_id " +
            "HAVING weeks >= ? " +
            "ORDER BY weeks DESC, last_eaten DESC LIMIT ?"

    // Params, in order: like, like, qLower, qLower, limit.
    const val SEARCH_RANKED =
        "SELECT $COLS_F FROM Food f $TRACK_JOIN " +
            "WHERE LOWER(f.name) LIKE ? " +
            "OR EXISTS (SELECT 1 FROM FoodAlias fa WHERE fa.food_id = f.id AND LOWER(fa.alias) LIKE ?) " +
            "ORDER BY (CASE " +
            "WHEN LOWER(f.name) = ? THEN 20 " +
            "WHEN INSTR(LOWER(f.name), ?) = 1 THEN 10 " +
            "ELSE 0 END + COALESCE(tc.track_count, 0)) DESC, f.name " +
            "LIMIT ?"
}

/**
 * Ranked relevance search cascade. Returns foods most-relevant first; empty if nothing matches.
 * Mirrors Lokcal's `FoodRepository.searchWithCounts` step for step.
 */
internal suspend fun searchCatalog(query: String, queries: LokcalSnapshotQueries): List<LokcalFood> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val qLower = trimmed.lowercase()
    val qNorm = normalize(qLower)

    // 1. Barcode: >5 digits → try exact GTIN-13 first.
    val digitsOnly = trimmed.filter { it.isDigit() }
    if (digitsOnly.length > 5) {
        val byBarcode = queries.selectByGtin13(digitsOnly)
        if (byBarcode.isNotEmpty()) return byBarcode
    }

    // 2. Ranked name/alias match on the longest token; for multi-word queries require every token present.
    val tokens = qNorm.split(Regex("\\s+"))
        .filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }
    val primary = tokens.maxByOrNull { it.length } ?: qNorm
    val candidates = queries.searchRanked("%$primary%", qNorm, SEARCH_LIMIT)
    if (candidates.isNotEmpty()) {
        return if (tokens.size >= 2) {
            candidates.filter { food -> tokens.all { t -> normalize(food.name.lowercase()).contains(t) } }
        } else {
            candidates
        }
    }

    // 3. Normalized full scan: handles accented names SQLite's LIKE can't match ("creme" ↔ "crème").
    val all = queries.selectAll()
    val normalizedMatches = if (tokens.size >= 2) {
        all.filter { f -> tokens.all { t -> normalize(f.name.lowercase()).contains(t) } }
    } else {
        all.filter { f -> normalize(f.name.lowercase()).contains(qNorm) }
    }
    if (normalizedMatches.isNotEmpty()) return normalizedMatches.take(SEARCH_LIMIT)

    // 4. Levenshtein fallback: edit distance ≤ 2 on normalized names, for typos.
    return all.filter { levenshtein(normalize(it.name.lowercase()), qNorm) <= 2 }.take(SEARCH_LIMIT)
}

private val charNormMap: Map<Char, String> = mapOf(
    'à' to "a", 'á' to "a", 'â' to "a", 'ã' to "a", 'ä' to "a", 'å' to "a",
    'æ' to "ae",
    'ç' to "c",
    'è' to "e", 'é' to "e", 'ê' to "e", 'ë' to "e",
    'ì' to "i", 'í' to "i", 'î' to "i", 'ï' to "i",
    'ð' to "d",
    'ñ' to "n",
    'ò' to "o", 'ó' to "o", 'ô' to "o", 'õ' to "o", 'ö' to "o", 'ø' to "o",
    'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u",
    'ý' to "y", 'ÿ' to "y",
    'þ' to "th",
    'ß' to "ss",
    'œ' to "oe",
    '&' to "and", // "Ben & Jerry's" ↔ "ben and jerry"
    '\'' to "", // strip ASCII apostrophe: "Jerry's" → "Jerrys"
    '’' to "", // strip right single quote (’): "Jerry's" → "Jerrys"
)

/** Normalizes characters for search: strips diacritics, maps "&" → "and", strips apostrophes. Lowercase input first. */
internal fun normalize(s: String): String = buildString(s.length) {
    for (c in s) append(charNormMap[c] ?: c)
}

internal fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val n = a.length
    val m = b.length
    val dp = IntArray(m + 1) { it }
    var prevDiag: Int
    var prev: Int
    for (i in 1..n) {
        prev = dp[0]
        dp[0] = i
        for (j in 1..m) {
            prevDiag = prev
            prev = dp[j]
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[j] = minOf(
                dp[j] + 1,
                dp[j - 1] + 1,
                prevDiag + cost,
            )
        }
    }
    return dp[m]
}
