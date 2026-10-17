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
    fun `a success clears the block`() {
        val guard = ImapLoginGuard().apply { recordAuthFailure("a:pw"); recordSuccess() }
        assertTrue(guard.shouldAttempt("a:pw"))
    }
}
