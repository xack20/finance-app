package app.hisaab.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHisaabPalette = staticCompositionLocalOf<HisaabColors.Palette> {
    error("HisaabTheme not provided")
}

@Composable
fun HisaabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) HisaabColors.Dark else HisaabColors.Light

    val material3Colors: ColorScheme = if (darkTheme) {
        darkColorScheme(
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            primary = palette.accent,
            onPrimary = palette.background,
            secondary = palette.gold,
            tertiary = palette.positive,
            error = palette.negative,
            outline = palette.rule,
        )
    } else {
        lightColorScheme(
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            primary = palette.accent,
            onPrimary = palette.background,
            secondary = palette.gold,
            tertiary = palette.positive,
            error = palette.negative,
            outline = palette.rule,
        )
    }

    val material3Typography = Typography(
        displayLarge   = HisaabTypography.heroAmount,
        headlineMedium = HisaabTypography.title,
        bodyMedium     = HisaabTypography.body,
        labelSmall     = HisaabTypography.label,
        bodySmall      = HisaabTypography.tabular,
    )

    CompositionLocalProvider(LocalHisaabPalette provides palette) {
        MaterialTheme(
            colorScheme = material3Colors,
            typography = material3Typography,
            shapes = HisaabShapes.material3(),
            content = content,
        )
    }
}
