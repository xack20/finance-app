package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/** Flatten a translucent fg token over an opaque bg, matching how Compose composites it. */
private fun flatten(fg: Color, bg: Color): Color = Color(
    red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
    green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
    blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
    alpha = 1f,
)

class ContrastTest {

    private fun aa(fg: Color, bg: Color, min: Float, label: String) {
        val r = Wcag.ratio(fg, bg)
        assertTrue(r >= min, "$label contrast ${(r * 100).toInt() / 100f} < $min")
    }

    @Test
    fun dark_palette_passes_aa() {
        val p = HisaabColors.Dark
        aa(p.onBackground, p.background, 4.5f, "dark text/bg")
        aa(p.muted, p.background, 4.5f, "dark muted/bg")
        aa(p.faint, p.background, 3.0f, "dark faint/bg")
        aa(p.onAccent, p.accent, 4.5f, "dark onAccent/accent")
        aa(p.positive, p.background, 3.0f, "dark positive/bg")
        aa(p.negative, p.background, 3.0f, "dark negative/bg")
        // on-card secondary text guarantee: muted is used for on-card labels (faint was replaced)
        aa(p.muted, p.surface, 3.0f, "dark muted/surface")
        aa(p.muted, p.surfaceRaised, 3.0f, "dark muted/surfaceRaised")
        val glassBg = flatten(p.glass, p.background)
        val bioBg = flatten(p.accentSoft, p.background)
        aa(p.faint, p.surface, 3.0f, "dark placeholder faint/surface")
        aa(p.faint, p.surfaceRaised, 3.0f, "dark disabled-label faint/surfaceRaised")
        aa(p.onBackground, p.surface, 4.5f, "dark input/otp/word text onBackground/surface")
        aa(p.accent, p.surface, 4.5f, "dark mono-prefix/recovery-index accent/surface")
        aa(p.accent, p.surface, 3.0f, "dark otp active border accent/surface (UI)")
        aa(p.negative, p.surface, 4.5f, "dark negative text/surface")
        aa(p.onBackground, glassBg, 4.5f, "dark glass-button label/glass-over-bg")
        aa(p.accent, bioBg, 3.0f, "dark biometric glyph accent/accentSoft-over-bg")
    }

    @Test
    fun light_palette_passes_aa() {
        val p = HisaabColors.Light
        aa(p.onBackground, p.background, 4.5f, "light text/bg")
        aa(p.muted, p.background, 4.5f, "light muted/bg")
        aa(p.faint, p.background, 3.0f, "light faint/bg")
        aa(p.onBackground, p.surface, 4.5f, "light text/surface")
        aa(p.accent, p.background, 4.5f, "light accent-as-text/bg")
        aa(p.onAccent, HisaabColors.LightAccentFill, 4.5f, "light onAccent/limeFill")
        aa(p.positive, p.surface, 4.5f, "light positive/surface")
        aa(p.negative, p.surface, 4.5f, "light negative/surface")
        // on-card secondary text guarantee: muted is used for on-card labels (faint was replaced)
        aa(p.muted, p.surface, 3.0f, "light muted/surface")
        aa(p.muted, p.surfaceRaised, 3.0f, "light muted/surfaceRaised")
        val glassBgL = flatten(p.glass, p.background)
        val bioBgL = flatten(p.accentSoft, p.background)
        aa(p.faint, p.surface, 3.0f, "light placeholder faint/surface")
        aa(p.faint, p.surfaceRaised, 3.0f, "light disabled-label faint/surfaceRaised")
        aa(p.accent, p.surface, 4.5f, "light mono-prefix/recovery-index accent/surface")
        aa(p.accent, p.surface, 3.0f, "light otp active border accent/surface (UI)")
        aa(p.onBackground, glassBgL, 4.5f, "light glass-button label/glass-over-bg")
        aa(p.accent, bioBgL, 3.0f, "light biometric glyph accent/accentSoft-over-bg")
    }
}
