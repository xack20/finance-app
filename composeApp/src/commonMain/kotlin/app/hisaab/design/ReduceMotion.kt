package app.hisaab.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** True when the OS "reduce motion" / "remove animations" accessibility setting is on.
 *  @Composable so the Android actual can read LocalContext without a process-wide holder. */
@Composable
expect fun isReduceMotionEnabled(): Boolean

/** App-wide reduce-motion flag, provided by HisaabTheme. Default false = motion on (safe fallback). */
val LocalReduceMotion = staticCompositionLocalOf { false }
