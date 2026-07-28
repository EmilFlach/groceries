package com.emilflach.groceries.ui.util

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory

/**
 * Installs a Coil image loader that can fetch remote food packaging photos
 * (the `https://lokcal.app/images/...` URLs on Lokcal foods) via Ktor. Call once,
 * high in the composition (see `App`), before any `AsyncImage` is used.
 */
@Composable
fun ConfigureCoilImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
}
