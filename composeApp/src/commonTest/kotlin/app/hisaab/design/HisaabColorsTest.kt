package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HisaabColorsTest {
    @Test
    fun light_cream_background_matches_spec() {
        assertEquals(Color(0xFFFAF7F2), HisaabColors.Light.background)
    }

    @Test
    fun light_terracotta_accent_matches_spec() {
        assertEquals(Color(0xFFAD6B2A), HisaabColors.Light.accent)
    }

    @Test
    fun dark_palette_differs_from_light() {
        assertNotEquals(HisaabColors.Light.background, HisaabColors.Dark.background)
        assertNotEquals(HisaabColors.Light.onBackground, HisaabColors.Dark.onBackground)
    }

    @Test
    fun forest_green_used_for_positive_amounts() {
        assertEquals(Color(0xFF2E7D4F), HisaabColors.Light.positive)
        assertEquals(Color(0xFF2E7D4F), HisaabColors.Dark.positive)
    }
}
