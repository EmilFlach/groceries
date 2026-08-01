package com.emilflach.groceries.mealie

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun mealieHttpEngineOrNull(): HttpClientEngineFactory<*>? = OkHttp
