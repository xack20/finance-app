package app.hisaab.screens.capture

import app.hisaab.data.AgentQuestionRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun message(id: String) = CandidateTransaction(
    id = id, receivedAt = 1L, channel = CaptureChannel.EMAIL, sender = "alerts@citybank.com",
    rawBody = "Card ending 1234 charged BDT 900", dedupHash = "h-$id", status = CaptureStatus.QUEUED, confidence = null,
    parsedBy = null, model = null, parseError = null, amount = null, direction = null, currency = "BDT",
    balanceAfter = null, refNo = null, proposedAccountId = null, proposedCategoryId = null, proposedMerchant = null,
    createdAt = 1L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private class Store {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val questions = AgentQuestionRepository(db)
        var kicks = 0

        suspend fun ask(): String {
            inbox.insertCandidate(message("m1"))
            return db.transactionWithResult { questions.askBlocking("Whose card ends 1234?", listOf("Mine"), listOf("m1")) }
        }

        fun vm() = QuestionsViewModel(
            openQuestions = questions.observeOpen(),
            messagesFor = inbox::messagesFor,
            answerQuestion = questions::answer,
            dismissQuestion = questions::dismiss,
            onAnswered = { kicks++ },
        )
    }

    @Test
    fun `each open question comes with the messages it is about`() = runTest {
        val s = Store()
        s.ask()
        val vm = s.vm()

        val card = vm.cards.first { it.isNotEmpty() }.single()

        assertEquals("Whose card ends 1234?", card.question.question)
        assertEquals(listOf("m1"), card.messages.map { it.id })
    }

    @Test
    fun `answering sends the answer back to the agent and wakes it`() = runTest {
        val s = Store()
        val id = s.ask()

        s.vm().answer(id, "  Mine  ")
        advanceUntilIdle()

        assertEquals("Mine", s.questions.getById(id)?.answer)
        assertEquals(CaptureStatus.QUEUED, s.inbox.getById("m1")?.status)
        assertEquals(1, s.kicks)
    }

    @Test
    fun `a blank answer is ignored`() = runTest {
        val s = Store()
        val id = s.ask()

        s.vm().answer(id, "   ")
        advanceUntilIdle()

        assertEquals(null, s.questions.getById(id)?.answer)
        assertEquals(0, s.kicks)
    }

    @Test
    fun `not a payment dismisses the question's messages`() = runTest {
        val s = Store()
        val id = s.ask()

        s.vm().notAPayment(id)
        advanceUntilIdle()

        assertEquals(CaptureStatus.DISMISSED, s.inbox.getById("m1")?.status)
        assertTrue(s.questions.observeOpen().first().isEmpty())
    }
}
