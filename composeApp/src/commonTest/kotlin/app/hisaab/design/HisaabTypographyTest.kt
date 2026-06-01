package app.hisaab.design

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HisaabTypographyTest {
    @Test
    fun display_is_space_grotesk() {
        assertTrue(
            HisaabTypography.display.familyDescription.contains("Space Grotesk", ignoreCase = true),
            "display should be Space Grotesk (got: ${HisaabTypography.display.familyDescription})",
        )
    }

    @Test
    fun ui_is_hanken_grotesk() {
        assertTrue(HisaabTypography.ui.familyDescription.contains("Hanken", ignoreCase = true))
    }

    @Test
    fun mono_is_space_mono() {
        assertTrue(HisaabTypography.mono.familyDescription.contains("Space Mono", ignoreCase = true))
    }

    @Test
    fun heroAmount_is_oversized_semibold() {
        val s = HisaabTypography.heroAmount
        assertTrue(s.fontSize.value >= 48f, "heroAmount should be ≥48sp (got ${s.fontSize})")
        assertEquals(FontWeight.SemiBold, s.fontWeight)
    }

    @Test
    fun eyebrow_is_tracked() {
        val s = HisaabTypography.eyebrow
        assertTrue(s.letterSpacing.value >= 1.0f, "eyebrow should be tracked (got ${s.letterSpacing})")
    }

    @Test
    fun tabular_numerals_enabled_on_money() {
        val features = HisaabTypography.tabular.fontFeatureSettings ?: ""
        assertTrue("tnum" in features, "money figures need tabular nums (got: $features)")
        assertTrue("lnum" in features, "money figures need lining nums (got: $features)")
    }
}
