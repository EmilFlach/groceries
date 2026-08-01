package com.emilflach.groceries.mealie

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun mealieHttpEngineOrNull(): HttpClientEngineFactory<*>? = Darwin
