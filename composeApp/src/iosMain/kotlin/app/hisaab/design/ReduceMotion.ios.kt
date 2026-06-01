package app.hisaab.design

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun isReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()
