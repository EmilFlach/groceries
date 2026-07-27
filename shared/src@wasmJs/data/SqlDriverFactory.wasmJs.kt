package com.emilflach.groceries.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

@OptIn(ExperimentalWasmJsInterop::class)
fun initializeWorker(): String = js(""" "./sqljs.worker.js" """)

object WorkerInstance {
    var worker: Worker? = null
}

actual class SqlDriverFactory {
    actual suspend fun createDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
        val worker = Worker(initializeWorker())
        WorkerInstance.worker = worker
        val driver = WebWorkerDriver(worker)
        if (!isMetaTablePresent(driver)) {
            schema.create(driver).await()
        }
        return driver
    }

    private suspend fun isMetaTablePresent(driver: SqlDriver): Boolean {
        val result = driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='Meta'",
            mapper = { cursor ->
                val hasNext = cursor.next()
                QueryResult.AsyncValue {
                    if (hasNext.await()) cursor.getLong(0) else 0L
                }
            },
            parameters = 0
        ).await()
        return result != 0L
    }
}
