package com.emilflach.groceries.ui.util

import androidx.compose.runtime.Composable

/** Desktop has no system back gesture; the on-screen back control dismisses the overlay. */
@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) {
}
