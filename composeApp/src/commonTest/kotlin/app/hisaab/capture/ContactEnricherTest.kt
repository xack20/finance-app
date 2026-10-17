package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactEnricherTest {

    private val book = mapOf("01712345678" to "Rahim Bhai")

    @Test
    fun `a known phone number gets the contact name`() {
        val out = enrichWithContacts("Send Money Tk 500.00 to 01712345678 successful.") { book[it] }
        assertEquals("Send Money Tk 500.00 to 01712345678 (contact: Rahim Bhai) successful.", out)
    }

    @Test
    fun `numbers written with the country code still match`() {
        val out = enrichWithContacts("Received Tk 200 from +8801712345678") { book[it] }
        assertEquals("Received Tk 200 from +8801712345678 (contact: Rahim Bhai)", out)
    }

    @Test
    fun `unknown numbers are left alone`() {
        val text = "Sent Tk 50 to 01899999999"
        assertEquals(text, enrichWithContacts(text) { book[it] })
    }
}
