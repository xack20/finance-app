package app.hisaab.mac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigTest {

    @Test
    fun parses_when_both_values_present_and_trims() {
        assertEquals(
            CompanionConfig("https://demo.supabase.co", "anon-key"),
            Config.parse("  https://demo.supabase.co ", " anon-key "),
        )
    }

    @Test
    fun null_when_either_value_missing_or_blank() {
        assertNull(Config.parse(null, "k"))
        assertNull(Config.parse("u", null))
        assertNull(Config.parse("", "k"))
        assertNull(Config.parse("u", "   "))
    }
}
