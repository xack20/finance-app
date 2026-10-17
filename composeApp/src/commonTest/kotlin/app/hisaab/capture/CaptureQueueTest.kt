package app.hisaab.capture

import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureQueueTest {

    private val raw = RawCapture("bKash", "You have received Tk 500.00 from 01712345678.", 1_000L, CaptureChannel.SMS)

    @Test
    fun `a message is queued once and wakes the agent once`() = runTest {
        val inbox = CaptureInboxRepository(TestDatabase.create())
        var wakes = 0
        val queue = CaptureQueue(inbox) { wakes++ }

        queue.handle(raw)
        queue.handle(raw)

        val queued = inbox.nextQueued(10).single()
        assertEquals(raw.body, queued.rawBody)
        assertEquals(CaptureStatus.QUEUED, queued.status)
        assertEquals(1, wakes)
    }

    @Test
    fun `a message the old engine already handled is not queued again`() = runTest {
        val inbox = CaptureInboxRepository(TestDatabase.create())
        var wakes = 0
        val queue = CaptureQueue(inbox) { wakes++ }
        queue.handle(raw)
        val earlier = inbox.nextQueued(10).single()
        // Same hash the rule-based pipeline stored: sha256(lowercased sender | trimmed body).
        assertEquals(Sha256.hexOf("bkash|" + raw.body.trim()), earlier.dedupHash)

        queue.handle(raw.copy(sender = " BKASH ", body = raw.body + "  "))

        assertEquals(1, wakes)
        assertTrue(inbox.nextQueued(10).size == 1)
    }
}
