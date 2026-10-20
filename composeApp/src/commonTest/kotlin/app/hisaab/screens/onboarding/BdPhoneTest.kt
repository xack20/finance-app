package app.hisaab.screens.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class BdPhoneTest {

    @Test
    fun `drops the trunk zero so +880 is not followed by 0`() {
        assertEquals("1712345678", bdLocalDigits("01712345678"))
    }

    @Test
    fun `strips a pasted country code with or without plus`() {
        assertEquals("1712345678", bdLocalDigits("+8801712345678"))
        assertEquals("1712345678", bdLocalDigits("8801712345678"))
    }

    @Test
    fun `keeps digits only and leaves an already-local number unchanged`() {
        assertEquals("1712345678", bdLocalDigits("17-1234 5678"))
        assertEquals("1712345678", bdLocalDigits("1712345678"))
    }
}
