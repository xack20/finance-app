package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class ImapUidCursorTest {

    @Test
    fun `a fresh cursor starts from the oldest matched mail`() {
        assertEquals(listOf(3L, 5L), ImapUidCursor.pending(listOf(5L, 3L), cursor = 0L, uidValidity = 42L))
    }

    @Test
    fun `resumes after the last UID read, oldest first`() {
        val cursor = ImapUidCursor.pack(uidValidity = 42L, uid = 5L)
        assertEquals(listOf(7L, 9L), ImapUidCursor.pending(listOf(5L, 3L, 9L, 7L), cursor, uidValidity = 42L))
    }

    @Test
    fun `a changed UIDVALIDITY restarts from the top because old UIDs are meaningless`() {
        val cursor = ImapUidCursor.pack(uidValidity = 41L, uid = 100L)
        assertEquals(listOf(1L, 2L), ImapUidCursor.pending(listOf(2L, 1L), cursor, uidValidity = 42L))
    }

    @Test
    fun `round-trips a UIDVALIDITY above the signed 32-bit range`() {
        val validity = 4_000_000_000L // UIDVALIDITY is an unsigned 32-bit value
        val cursor = ImapUidCursor.pack(validity, uid = 10L)
        assertEquals(listOf(11L), ImapUidCursor.pending(listOf(10L, 11L), cursor, validity))
    }
}
