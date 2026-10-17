package app.hisaab.capture.agent

import app.hisaab.capture.ChannelStatus
import app.hisaab.capture.ChannelStatusTracker
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun msg(id: String) = CandidateTransaction(
    id = id, receivedAt = id.hashCode().toLong(), channel = CaptureChannel.SMS, sender = "bKash", rawBody = id,
    dedupHash = "h-$id", status = CaptureStatus.QUEUED, confidence = null, parsedBy = null, model = null,
    parseError = null, amount = null, direction = null, currency = "BDT", balanceAfter = null, refNo = null,
    proposedAccountId = null, proposedCategoryId = null, proposedMerchant = null, createdAt = 1L,
)

class CaptureAgentWorkerTest {

    private class FakeBudget(var calls: Int = 0) : AgentCallBudget {
        override suspend fun callsToday() = calls
        override suspend fun recordCall() { calls++ }
    }

    private class Store {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val status = ChannelStatusTracker(now = { 42L })
        val batches = mutableListOf<List<String>>()

        /** Settles every message it is given, like a well-behaved agent. */
        val settleAll = BatchProcessor { batch ->
            batches += batch.map { it.id }
            batch.forEach { db.captureInboxQueriesQueries.markDecided("DISMISSED", "test", it.id) }
            BatchOutcome(emptyList(), decided = batch.size, undecided = 0)
        }

        suspend fun queue(vararg ids: String) = ids.forEach { inbox.insertCandidate(msg(it)) }
        suspend fun status(id: String) = inbox.getById(id)?.status

        fun worker(processor: BatchProcessor?, budget: AgentCallBudget = FakeBudget(), batchSize: Int = 25) =
            CaptureAgentWorker(inbox, { processor }, budget, status, batchSize = batchSize, dailyCap = 600)
    }

    @Test
    fun `kicks within the debounce window run one drain`() = runTest {
        val s = Store()
        s.queue("a", "b", "c")
        val worker = s.worker(s.settleAll, batchSize = 2)
        worker.start(backgroundScope)

        worker.kick(); worker.kick(); worker.kick()
        advanceTimeBy(4_999); runCurrent()
        assertTrue(s.batches.isEmpty()) // still waiting for related messages to arrive

        advanceTimeBy(2); runCurrent()
        assertEquals(2, s.batches.size) // one drain, two batches of at most 2
        assertEquals(CaptureStatus.DISMISSED, s.status("c"))
        assertEquals(ChannelStatus.UpToDate(42L), s.status.status.value)
    }

    @Test
    fun `stops at the daily cap with the queue intact`() = runTest {
        val s = Store()
        s.queue("a")

        s.worker(s.settleAll, budget = FakeBudget(calls = 600)).runUntilIdle()

        assertTrue(s.batches.isEmpty())
        assertEquals(CaptureStatus.QUEUED, s.status("a"))
        assertTrue("limit" in (s.status.status.value as ChannelStatus.Error).reason)
    }

    @Test
    fun `no AI available leaves messages queued and says why`() = runTest {
        val s = Store()
        s.queue("a")

        s.worker(processor = null).runUntilIdle()

        assertEquals(CaptureStatus.QUEUED, s.status("a"))
        assertTrue("key" in (s.status.status.value as ChannelStatus.Error).reason)
    }

    @Test
    fun `a rejected key stops the drain`() = runTest {
        val s = Store()
        s.queue("a", "b")
        var calls = 0
        val rejecting = BatchProcessor { calls++; throw LlmException(LlmError.InvalidKey) }

        s.worker(rejecting, batchSize = 1).runUntilIdle()

        assertEquals(1, calls)
        assertEquals(CaptureStatus.QUEUED, s.status("a"))
        assertTrue("rejected" in (s.status.status.value as ChannelStatus.Error).reason)
    }

    @Test
    fun `progress is reported while draining`() = runTest {
        val s = Store()
        s.queue("a", "b", "c")
        val seen = mutableListOf<ChannelStatus>()
        val watching = BatchProcessor { batch -> seen += s.status.status.value; s.settleAll.process(batch) }

        s.worker(watching, batchSize = 1).runUntilIdle()

        assertEquals(ChannelStatus.Importing(0, 3), seen.first())
        assertEquals(ChannelStatus.Importing(2, 3), seen.last())
    }
}
