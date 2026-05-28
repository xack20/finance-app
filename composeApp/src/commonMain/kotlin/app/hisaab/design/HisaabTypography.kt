package app.hisaab.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HisaabTypography {

    data class Family(val familyDescription: String, val compose: FontFamily)

    val display = Family("serif (system fallback; GT Sectra in P0b)", FontFamily.Serif)
    val ui      = Family("system sans (Inter in P0b)", FontFamily.SansSerif)
    val mono    = Family("monospace (JetBrains Mono in P0b)", FontFamily.Monospace)

    val heroAmount: TextStyle = TextStyle(
        fontFamily = display.compose,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        letterSpacing = (-0.6).sp,
        lineHeight = 48.sp,
    )

    val title: TextStyle = TextStyle(
        fontFamily = display.compose,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        letterSpacing = (-0.2).sp,
        lineHeight = 28.sp,
    )

    val body: TextStyle = TextStyle(
        fontFamily = ui.compose,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val label: TextStyle = TextStyle(
        fontFamily = ui.compose,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.8.sp,
        lineHeight = 12.sp,
    )

    val tabular: TextStyle = TextStyle(
        fontFamily = mono.compose,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum, lnum",
    )
}
