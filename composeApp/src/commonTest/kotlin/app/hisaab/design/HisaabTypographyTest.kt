package app.hisaab.design

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HisaabTypographyTest {
    @Test
    fun display_uses_serif_family() {
        val name = HisaabTypography.display.familyDescription
        assertTrue(
            name.contains("serif", ignoreCase = true),
            "display family should be serif (got: $name)",
        )
    }

    @Test
    fun ui_uses_sans_family() {
        val name = HisaabTypography.ui.familyDescription
        assertTrue(
            name.contains("sans", ignoreCase = true) || name.contains("system", ignoreCase = true),
            "ui family should be sans/system (got: $name)",
        )
    }

    @Test
    fun mono_uses_monospace_family() {
        val name = HisaabTypography.mono.familyDescription
        assertTrue(
            name.contains("mono", ignoreCase = true),
            "mono family should be monospace (got: $name)",
        )
    }

    @Test
    fun heroAmount_is_large_and_regular() {
        val style = HisaabTypography.heroAmount
        assertTrue(style.fontSize.value >= 40f, "heroAmount should be ≥40sp (got ${style.fontSize})")
        assertEquals(FontWeight.Normal, style.fontWeight)
    }

    @Test
    fun tabular_numerals_enabled_on_mono() {
        val features = HisaabTypography.tabular.fontFeatureSettings ?: ""
        assertTrue(
            "tnum" in features,
            "tabular style must enable tnum feature (got: $features)",
        )
    }
}
