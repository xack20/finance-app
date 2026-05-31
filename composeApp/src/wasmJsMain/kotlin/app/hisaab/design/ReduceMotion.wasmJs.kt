package app.hisaab.design

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun isReduceMotionEnabled(): Boolean =
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
