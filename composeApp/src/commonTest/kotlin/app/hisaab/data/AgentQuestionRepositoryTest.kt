package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun row(id: String) = CandidateTransaction(
    id = id, receivedAt = 1L, channel = CaptureChannel.EMAIL, sender = "alerts@citybank.com",
    rawBody = "b-$id", dedupHash = "h-$id", status = CaptureStatus.QUEUED, confidence = null, parsedBy = null,
    model = null, parseError = null, amount = null, direction = null, currency = "BDT", balanceAfter = null,
    refNo = null, proposedAccountId = null, proposedCategoryId = null, proposedMerchant = null, createdAt = 1L,
)

class AgentQuestionRepositoryTest {

    private class Store {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val questions = AgentQuestionRepository(db)

        suspend fun ask(): String {
            inbox.insertCandidate(row("m1"))
            inbox.insertCandidate(row("m2"))
            return db.transactionWithResult {
                questions.askBlocking("Whose card is this?", listOf("Mine", "My wife's"), listOf("m1", "m2"))
            }
        }
    }

    @Test
    fun `asking parks the messages on an open question`() = runTest {
        val s = Store()
        s.ask()

        val open = s.questions.observeOpen().first().single()
        assertEquals("Whose card is this?", open.question)
        assertEquals(listOf("Mine", "My wife's"), open.options)
        assertEquals(listOf("m1", "m2"), s.inbox.messagesFor(open.id).map { it.id })
        assertEquals(CaptureStatus.PENDING, s.inbox.getById("m1")?.status)
        assertEquals(1L, s.questions.observeOpenCount().first())
    }

    @Test
    fun `answering re-queues the question's messages with the answer kept`() = runTest {
        val s = Store()
        val id = s.ask()

        s.questions.answer(id, "Mine")

        assertTrue(s.questions.observeOpen().first().isEmpty())
        assertEquals(CaptureStatus.QUEUED, s.inbox.getById("m1")?.status)
        assertEquals(CaptureStatus.QUEUED, s.inbox.getById("m2")?.status)
        assertEquals("Mine", s.questions.getById(id)?.answer)
    }

    @Test
    fun `dismissing a question dismisses its messages`() = runTest {
        val s = Store()
        val id = s.ask()

        s.questions.dismiss(id)

        assertTrue(s.questions.observeOpen().first().isEmpty())
        assertEquals(CaptureStatus.DISMISSED, s.inbox.getById("m1")?.status)
    }

    @Test
    fun `memories can be added and deleted`() = runTest {
        val db = TestDatabase.create()
        val memory = AgentMemoryRepository(db)
        memory.addBlocking("alerts@citybank.com is the City Bank card")

        val fact = memory.all().single()
        assertEquals("alerts@citybank.com is the City Bank card", fact.fact)
        memory.delete(fact.id)
        assertTrue(memory.all().isEmpty())
    }

    @Test
    fun `AI calls are counted per day and reset on a new day`() = runTest {
        val config = CaptureConfigRepository(TestDatabase.create())
        config.recordAgentCall("2026-10-17")
        config.recordAgentCall("2026-10-17")
        assertEquals(2, config.agentCallsToday("2026-10-17"))

        assertEquals(0, config.agentCallsToday("2026-10-18"))
        config.recordAgentCall("2026-10-18")
        assertEquals(1, config.agentCallsToday("2026-10-18"))
    }
}
