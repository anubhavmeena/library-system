package com.targetzone.library.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// Central haptic vocabulary for the whole app — a fixed, mechanical tiering
// rather than a per-screen judgment call: tick() for navigation/selection/taps,
// confirm() for success/primary-CTA completions, reject() for destructive
// actions and validation failures. Built on the Compose-level LocalHapticFeedback
// API (not raw View.performHapticFeedback), so OS-version gating is handled
// internally — no manual SDK checks or VIBRATE permission needed.
class LibraryHaptics(private val haptic: HapticFeedback) {
    fun tick() = haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
    fun confirm() = haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    fun reject() = haptic.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberLibraryHaptics(): LibraryHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { LibraryHaptics(haptic) }
}
