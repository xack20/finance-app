package app.hisaab.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HisaabShapesTest {
    @Test
    fun midnight_radii_match_spec() {
        assertEquals(RoundedCornerShape(22.dp), HisaabShapes.card)
        assertEquals(RoundedCornerShape(16.dp), HisaabShapes.field)
        assertEquals(RoundedCornerShape(999.dp), HisaabShapes.pill)
    }

    @Test
    fun sheet_has_26dp_top_radius_only() {
        assertEquals(
            RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            HisaabShapes.sheet,
        )
    }
}
