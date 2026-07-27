package com.emilflach.groceries.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

actual class SqlDriverFactory {
    actual suspend fun createDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
        // This JVM driver is used only by the desktop app (`./kotlin run -m desktopApp`,
        // which runs from the repo root), so keep its dev database under desktopApp/.
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:desktopApp/groceries.db", Properties(), schema.synchronous())
        return driver
    }
}
