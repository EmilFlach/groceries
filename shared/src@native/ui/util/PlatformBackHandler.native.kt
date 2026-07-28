package com.emilflach.groceries.ui.util

import androidx.compose.runtime.Composable

/** iOS dismisses the overlay via its on-screen back control; there's no system back to intercept here. */
@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) {
}
