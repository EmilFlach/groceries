package com.emilflach.groceries.mealie

import io.ktor.client.engine.HttpClientEngineFactory

// No Ktor engine is wired for wasmJs, so Mealie is unavailable on web — [MealieClient] becomes a
// no-op returning empty results (recipes simply don't appear in web search).
actual fun mealieHttpEngineOrNull(): HttpClientEngineFactory<*>? = null
