package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HisaabColorsTest {
    @Test
    fun dark_canvas_is_deep_ink() {
        assertEquals(Color(0xFF0A0B0E), HisaabColors.Dark.background)
        assertEquals(Color(0xFF161922), HisaabColors.Dark.surface)
    }

    @Test
    fun electric_lime_is_the_accent() {
        assertEquals(Color(0xFFCBF24A), HisaabColors.Dark.accent)
        assertEquals(Color(0xFF0A0B0E), HisaabColors.Dark.onAccent)
    }

    @Test
    fun dark_text_roles_match_spec() {
        assertEquals(Color(0xFFF3F5F8), HisaabColors.Dark.onBackground)
        assertEquals(Color(0xFF98A0AD), HisaabColors.Dark.muted)
        assertEquals(Color(0xFF5C6470), HisaabColors.Dark.faint)
    }

    @Test
    fun semantic_money_colors_match_spec() {
        assertEquals(Color(0xFF46E08A), HisaabColors.Dark.positive)
        assertEquals(Color(0xFFFF6B5C), HisaabColors.Dark.negative)
    }

    @Test
    fun light_variant_is_a_pale_canvas_with_ink_text() {
        assertEquals(Color(0xFFF6F8FB), HisaabColors.Light.background)
        assertEquals(Color(0xFF13161B), HisaabColors.Light.onBackground)
    }

    @Test
    fun light_and_dark_differ() {
        assertNotEquals(HisaabColors.Light.background, HisaabColors.Dark.background)
        assertNotEquals(HisaabColors.Light.onBackground, HisaabColors.Dark.onBackground)
    }

    @Test
    fun eight_category_hues_present() {
        assertEquals(8, HisaabColors.categoryHues.size)
        assertEquals(Color(0xFF8B7CFF), HisaabColors.categoryHues["violet"])
    }
}
