package app.hisaab.mac

import app.hisaab.capture.ImapHosts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImapHostsTest {

    @Test
    fun maps_known_consumer_providers() {
        assertEquals("imap.gmail.com" to 993, ImapHosts.forEmail("me@gmail.com"))
        assertEquals("outlook.office365.com" to 993, ImapHosts.forEmail("me@outlook.com"))
        assertEquals("imap.mail.yahoo.com" to 993, ImapHosts.forEmail("me@YAHOO.com")) // case-insensitive
        assertEquals("imap.mail.me.com" to 993, ImapHosts.forEmail("me@icloud.com"))
    }

    @Test
    fun null_for_unknown_or_malformed() {
        assertNull(ImapHosts.forEmail("me@company.com")) // Workspace/custom → needs OAuth, not here
        assertNull(ImapHosts.forEmail("notanemail"))
    }
}
