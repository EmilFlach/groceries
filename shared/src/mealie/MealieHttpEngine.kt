package com.emilflach.groceries.mealie

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * The Ktor engine used by [MealieClient], or `null` on platforms without one (web/wasmJs has no
 * engine wired). A null engine makes [MealieClient] a no-op that returns empty results — the same
 * "degrade to empty where the source is unreachable" approach the Lokcal bridge uses.
 *
 * All Mealie logic lives in common code; only the engine differs per platform, so this tiny
 * `expect fun` is the single platform seam.
 */
expect fun mealieHttpEngineOrNull(): HttpClientEngineFactory<*>?
