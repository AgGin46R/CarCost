package com.aggin.carcost.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Returns a lambda you call to trigger a light click haptic.
 * Usage:
 *   val haptic = rememberHapticClick()
 *   Button(onClick = { haptic(); doWork() })
 */
@Composable
fun rememberHapticClick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/**
 * Stronger haptic — use for destructive actions (delete, long-press).
 */
@Composable
fun rememberHapticLongPress(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

/**
 * Direct View-based haptic for non-composable contexts.
 */
fun View.hapticClick() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

fun View.hapticLongPress() {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}
