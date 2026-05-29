package app.hisaab.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactorTest {

    @Test
    fun `masks account number keeping last 4`() {
        val out = Redactor.redact("Payment to A/C 1234567890 successful")
        assertTrue(out.contains("[ACCT *7890]"), out)
        assertFalse(out.contains("1234567890"))
    }

    @Test
    fun `masks bd phone number keeping last 4`() {
        val out = Redactor.redact("Sent to 01712345678 from bKash")
        assertTrue(out.contains("[PHONE *5678]"), out)
        assertFalse(out.contains("01712345678"))
    }

    @Test
    fun `preserves amount and currency markers`() {
        val out = Redactor.redact("You have received Tk 1,500.00 from 01712345678")
        assertTrue(out.contains("Tk 1,500.00"), out)
        assertTrue(out.contains("[PHONE *5678]"), out)
    }

    @Test
    fun `masks capitalised name tokens to NAME placeholder`() {
        // "Mr Rahim Uddin" — two-word capitalised proper-name run after a title.
        val out = Redactor.redact("Cash out to Mr Rahim Uddin agent")
        assertTrue(out.contains("[NAME]"), out)
        assertFalse(out.contains("Rahim Uddin"), out)
    }

    @Test
    fun `does not mask known financial keywords`() {
        val out = Redactor.redact("bKash Payment TrxID 9AB12CD34")
        assertTrue(out.contains("TrxID 9AB12CD34"), out)
        assertTrue(out.contains("bKash"), out)
    }

    @Test
    fun `is idempotent`() {
        val once = Redactor.redact("Sent to 01712345678 A/C 1234567890")
        val twice = Redactor.redact(once)
        assertEquals(once, twice)
    }

    @Test
    fun `short digit runs like trx amounts are not treated as accounts`() {
        val out = Redactor.redact("Fee Tk 5.00 ref 123")
        assertTrue(out.contains("ref 123"), out)
        assertFalse(out.contains("[ACCT"), out)
    }
}
