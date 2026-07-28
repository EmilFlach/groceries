package com.emilflach.groceries.ui.util

import androidx.compose.runtime.Composable

/**
 * Routes the system back gesture to [onBack]. Only Android has one to intercept (via the
 * non-deprecated `androidx.activity.compose.BackHandler`); other targets no-op.
 */
@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)
