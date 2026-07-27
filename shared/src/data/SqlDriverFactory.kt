package com.emilflach.groceries.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.emilflach.groceries.Database

expect class SqlDriverFactory {
    suspend fun createDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver
}

suspend fun createDatabase(sqlDriverFactory: SqlDriverFactory): Database {
    val driver = sqlDriverFactory.createDriver(schema = Database.Schema)
    return Database(driver)
}
