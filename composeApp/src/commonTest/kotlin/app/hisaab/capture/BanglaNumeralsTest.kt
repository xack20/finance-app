package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class BanglaNumeralsTest {

    @Test
    fun `converts all bangla digits to ascii`() {
        assertEquals("0123456789", BanglaNumerals.normalize("০১২৩৪৫৬৭৮৯"))
    }

    @Test
    fun `leaves ascii digits and letters untouched`() {
        assertEquals("Tk 1500.50", BanglaNumerals.normalize("Tk 1500.50"))
    }

    @Test
    fun `converts mixed bangla and ascii in an amount`() {
        // "Tk ১,৫০০" -> "Tk 1,500"
        assertEquals("Tk 1,500", BanglaNumerals.normalize("Tk ১,৫০০"))
    }

    @Test
    fun `empty string returns empty`() {
        assertEquals("", BanglaNumerals.normalize(""))
    }
}
