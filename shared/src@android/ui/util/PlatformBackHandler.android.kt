package com.emilflach.groceries.ui.util

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** Delegates to activity-compose's back handler, which routes the OnBackPressedDispatcher/back gesture. */
@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
}
