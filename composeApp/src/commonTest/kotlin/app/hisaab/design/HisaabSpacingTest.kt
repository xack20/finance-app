package app.hisaab.design

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HisaabSpacingTest {
    @Test
    fun spacing_scale_uses_4dp_base_grid() {
        assertEquals(4.dp,  HisaabSpacing.xs)
        assertEquals(8.dp,  HisaabSpacing.sm)
        assertEquals(12.dp, HisaabSpacing.md)
        assertEquals(16.dp, HisaabSpacing.lg)
        assertEquals(20.dp, HisaabSpacing.gutter)
        assertEquals(32.dp, HisaabSpacing.xl)
        assertEquals(48.dp, HisaabSpacing.xxl)
    }
}
