package app.hisaab.screens.entry

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountKeyTest {
    @Test fun first_digit_replaces_empty() = assertEquals("5", applyAmountKey("", '5'))
    @Test fun digit_replaces_leading_zero() = assertEquals("5", applyAmountKey("0", '5'))
    @Test fun digits_append() = assertEquals("123", applyAmountKey("12", '3'))
    @Test fun dot_appends() = assertEquals("12.", applyAmountKey("12", '.'))
    @Test fun dot_on_empty_makes_zero_point() = assertEquals("0.", applyAmountKey("", '.'))
    @Test fun second_dot_ignored() = assertEquals("12.", applyAmountKey("12.", '.'))
    @Test fun max_two_decimals() {
        assertEquals("12.50", applyAmountKey("12.5", '0'))
        assertEquals("12.50", applyAmountKey("12.50", '5'))
    }
    @Test fun backspace_drops_last() = assertEquals("12", applyAmountKey("123", '⌫'))
    @Test fun backspace_on_empty_stays_empty() = assertEquals("", applyAmountKey("", '⌫'))
    @Test fun integer_length_capped() = assertEquals("123456789012", applyAmountKey("123456789012", '3'))
}
