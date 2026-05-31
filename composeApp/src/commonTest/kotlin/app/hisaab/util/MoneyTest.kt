package app.hisaab.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    @Test fun groups_thousands_western_style() {
        assertEquals("0", 0L.grouped())
        assertEquals("999", 999L.grouped())
        assertEquals("1,200", 1200L.grouped())
        assertEquals("62,250", 62250L.grouped())
        assertEquals("1,234,567", 1234567L.grouped())
    }

    @Test fun taka_unsigned_whole() {
        assertEquals("৳1,200", 1200.0.toTaka())
        assertEquals("৳0", 0.0.toTaka())
    }

    @Test fun taka_negative_uses_minus_sign_and_symbol() {
        assertEquals("−৳1,250", (-1250.0).toTaka()) // U+2212 MINUS
    }

    @Test fun taka_signed_shows_plus_for_positive() {
        assertEquals("+৳62,250", 62250.0.toTaka(signed = true))
        assertEquals("−৳1,250", (-1250.0).toTaka(signed = true))
        assertEquals("৳0", 0.0.toTaka(signed = true)) // zero is never signed
    }

    @Test fun taka_with_decimals_groups_whole_only() {
        assertEquals("৳1,200.50", 1200.5.toTaka(decimals = 2))
    }
}
