package app.hisaab

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformInfoTest {
    @Test
    fun platformName_isNotBlank() {
        val info = PlatformInfo()
        assertNotNull(info.name)
        assertTrue(info.name.isNotBlank(), "PlatformInfo.name must not be blank")
    }
}
