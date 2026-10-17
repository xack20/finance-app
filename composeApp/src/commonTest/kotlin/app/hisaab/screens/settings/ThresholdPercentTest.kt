package app.hisaab.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ThresholdPercentTest {

    @Test
    fun `slider floats round to the percent the user picked`() {
        assertEquals(29, thresholdPercent(0.29f.toDouble())) // 0.28999… must not show as 28%
        assertEquals(40, thresholdPercent(0.40f.toDouble()))
        assertEquals(85, thresholdPercent(DEFAULT_AUTO_POST_THRESHOLD))
    }
}
