package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImapLoginGuardTest {

    @Test
    fun `a rejected login blocks retries with the same credentials`() {
        val guard = ImapLoginGuard().apply { recordAuthFailure("a:pw") }
        assertFalse(guard.shouldAttempt("a:pw")) // don't hammer Gmail every 30 s with a known-bad password
    }

    @Test
    fun `new credentials are tried again`() {
        val guard = ImapLoginGuard().apply { recordAuthFailure("a:pw") }
        assertTrue(guard.shouldAttempt("a:pw2"))
    }

    @Test
    fun `the same credentials are retried after 15 minutes`() {
        var now = 0L
        val guard = ImapLoginGuard(now = { now }).apply { recordAuthFailure("a:pw") }
        now = 14 * 60 * 1000L
        assertFalse(guard.shouldAttempt("a:pw"))
        now = 15 * 60 * 1000L
        assertTrue(guard.shouldAttempt("a:pw")) // e.g. IMAP was just enabled on the account
    }

    @Test
    fun `a success clears the block`() {
        val guard = ImapLoginGuard().apply { recordAuthFailure("a:pw"); recordSuccess() }
        assertTrue(guard.shouldAttempt("a:pw"))
    }
}
