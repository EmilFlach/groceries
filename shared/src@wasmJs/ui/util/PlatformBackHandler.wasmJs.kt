package com.emilflach.groceries.ui.util

import androidx.compose.runtime.Composable

/** Web dismisses the overlay via its on-screen back control; the browser back button isn't wired here. */
@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) {
}
