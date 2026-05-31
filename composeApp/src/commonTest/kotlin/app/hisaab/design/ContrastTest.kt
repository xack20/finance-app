package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

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
    }
}
