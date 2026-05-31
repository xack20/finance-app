package app.hisaab.design

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
    darkTheme: Boolean = true, // Midnight is dark-primary; pass false for the derived light variant.
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) HisaabColors.Dark else HisaabColors.Light

    val scheme: ColorScheme = if (darkTheme) {
        darkColorScheme(
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceRaised,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            onSurfaceVariant = palette.muted,
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentDim,
            tertiary = palette.positive,
            error = palette.negative,
            outline = palette.rule,
        )
    } else {
        lightColorScheme(
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceRaised,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            onSurfaceVariant = palette.muted,
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentDim,
            tertiary = palette.positive,
            error = palette.negative,
            outline = palette.rule,
        )
    }

    // Bind the bundled OFL families into the Midnight text scale. `families()` is @Composable
    // (Compose-Resources Font loads in composition), so it can't be wrapped in remember {} — but
    // HisaabTheme sits at the app root and rarely recomposes, and Compose Resources caches font
    // loads, so reconstructing the FontFamilies here is negligible.
    val fams = HisaabTypography.families()
    val typography = Typography(
        displayLarge   = HisaabTypography.heroAmount.copy(fontFamily = fams.display),
        headlineMedium = HisaabTypography.title.copy(fontFamily = fams.display),
        labelLarge     = HisaabTypography.eyebrow.copy(fontFamily = fams.ui),
        bodyMedium     = HisaabTypography.body.copy(fontFamily = fams.ui),
        labelSmall     = HisaabTypography.label.copy(fontFamily = fams.ui),
        bodySmall      = HisaabTypography.tabular.copy(fontFamily = fams.mono),
    )

    CompositionLocalProvider(
        LocalHisaabPalette provides palette,
        LocalReduceMotion provides isReduceMotionEnabled(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = HisaabShapes.material3(),
            content = content,
        )
    }
}
