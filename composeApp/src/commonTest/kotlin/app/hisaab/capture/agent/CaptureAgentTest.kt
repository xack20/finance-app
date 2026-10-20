package app.hisaab.capture.agent

import app.hisaab.agent.support.FakeAgentProvider
import app.hisaab.data.AccountRepository
import app.hisaab.data.AgentMemoryRepository
import app.hisaab.data.AgentQuestionRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureAgentTest {

    private fun msg(id: String, body: String, at: Long) = CandidateTransaction(
        id = id, receivedAt = at, channel = CaptureChannel.SMS, sender = "bKash", rawBody = body, dedupHash = "h-$id",
        status = CaptureStatus.QUEUED, confidence = null, parsedBy = null, model = null, parseError = null,
        amount = null, direction = null, currency = "BDT", balanceAfter = null, refNo = null, proposedAccountId = null,
        proposedCategoryId = null, proposedMerchant = null, createdAt = at,
    )

    private fun final(vararg writes: String) =
        """{"thought":"t","final":{"message":"done","proposedWrites":[${writes.joinToString(",")}]}}"""

    private class Store {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val accounts = AccountRepository(db)
        val categories = CategoryRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val questions = AgentQuestionRepository(db)
        val memory = AgentMemoryRepository(db)
        val people = PersonRepository(db)
        private val committer =
            CaptureCommitter(db, accounts, categories, txns, people, LendBorrowRepository(db, txns), questions, memory)

        fun agent(
            provider: app.hisaab.agent.AgentProvider,
            maxIterations: Int = 6,
            extraAccounts: List<app.hisaab.domain.Account> = emptyList(),
            prepareSender: suspend (String) -> String = { it },
        ) = CaptureAgent(
            provider = provider,
            readTools = emptyList(),
            context = {
                AgentContext(
                    accounts.observeActive().first() + extraAccounts, categories.observeAll().first(), emptyList(),
                    memory.all().map { it.fact },
                )
            },
            committer = committer,
            inbox = inbox,
            questions = questions,
            prepareSender = prepareSender,
            timeZone = TimeZone.UTC,
            today = { "2026-10-17" },
            maxIterations = maxIterations,
        )

        suspend fun queue(vararg messages: CandidateTransaction): List<CandidateTransaction> {
            accounts.ensureDefaultCashAccount()
            categories.ensureDefaults()
            messages.forEach { inbox.insertCandidate(it) }
            return inbox.nextQueued(25)
        }

        suspend fun status(id: String) = inbox.getById(id)?.status
    }

    @Test
    fun `a batch is decided and committed`() = runTest {
        val s = Store()
        val batch = s.queue(msg("a", "Payment Tk 120 to Foodpanda", 1_000), msg("b", "Get 50% off today!", 2_000))
        val provider = FakeAgentProvider(
            listOf(
                final(
                    """{"tool":"post_transaction","args":{"messages":["1"],"account":"Cash","amount":120,"kind":"EXPENSE","category":"food","merchant":"Foodpanda"}}""",
                    """{"tool":"skip","args":{"messages":["2"],"reason":"promo"}}""",
                ),
            ),
        )

        val outcome = s.agent(provider).process(batch)

        assertEquals(1, s.txns.observeRecent(10).first().size)
        assertEquals(CaptureStatus.AUTO_POSTED, s.status("a"))
        assertEquals(CaptureStatus.DISMISSED, s.status("b"))
        assertEquals(1, outcome.events.size)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `the prompt carries accounts, memories, earlier answers and short message numbers`() = runTest {
        val s = Store()
        s.db.transaction { s.memory.addBlocking("16216 is the user's bKash") }
        s.queue(msg("a", "Card ending 1234 charged Tk 900", 1_000))
        val questionId = s.db.transactionWithResult { s.questions.askBlocking("Whose card ends 1234?", emptyList(), listOf("a")) }
        s.questions.answer(questionId, "My City Bank card")
        val batch = s.inbox.nextQueued(25)
        val provider = FakeAgentProvider(listOf(final("""{"tool":"skip","args":{"messages":["1"],"reason":"x"}}""")))

        s.agent(provider).process(batch)

        val prompt = provider.calls.single().joinToString("\n") { it.content }
        assertTrue("Cash" in prompt)
        assertTrue("16216 is the user's bKash" in prompt)
        assertTrue("Whose card ends 1234?" in prompt && "My City Bank card" in prompt)
        assertTrue("[1]" in prompt && "Card ending 1234 charged Tk 900" in prompt)
        assertTrue("1970-01-01 00:00" in prompt) // received time, in the user's zone
        assertFalse("\"a\"" in prompt) // database ids never reach the model
    }

    @Test
    fun `a validation error gets one repair round`() = runTest {
        val s = Store()
        val batch = s.queue(msg("a", "Paid Tk 60 for rickshaw", 1_000))
        val provider = FakeAgentProvider(
            listOf(
                final("""{"tool":"post_transaction","args":{"messages":["1"],"account":"Ghost","amount":60,"kind":"EXPENSE"}}"""),
                final("""{"tool":"post_transaction","args":{"messages":["1"],"account":"Cash","amount":60,"kind":"EXPENSE"}}"""),
            ),
        )

        s.agent(provider).process(batch)

        assertEquals(2, provider.callCount)
        assertTrue(provider.calls[1].any { "unknown account 'Ghost'" in it.content })
        assertEquals(CaptureStatus.AUTO_POSTED, s.status("a"))
    }

    @Test
    fun `malformed replies leave the batch queued with attempts bumped`() = runTest {
        val s = Store()
        val batch = s.queue(msg("a", "Paid Tk 60", 1_000))
        val provider = FakeAgentProvider(listOf("not json", "still not json"))

        s.agent(provider, maxIterations = 2).process(batch)

        assertEquals(CaptureStatus.QUEUED, s.status("a"))
        assertEquals(1, s.inbox.getById("a")?.attempts)
        assertTrue(s.txns.observeRecent(10).first().isEmpty())
    }

    @Test
    fun `a message undecided three times becomes a question`() = runTest {
        val s = Store()
        s.queue(msg("a", "XJ-77 ref 0091 done", 1_000))
        s.inbox.bumpAttempts("a")
        s.inbox.bumpAttempts("a")
        val provider = FakeAgentProvider(listOf(final()))

        s.agent(provider).process(s.inbox.nextQueued(25))

        assertEquals(CaptureStatus.PENDING, s.status("a"))
        assertTrue("couldn't work out" in s.questions.observeOpen().first().single().question)
    }

    @Test
    fun `a batch that can't be written is left for another try`() = runTest {
        val s = Store()
        val batch = s.queue(msg("a", "Paid Tk 60", 1_000))
        // The agent was told about an account that is gone by the time the batch is written.
        val phantom = app.hisaab.domain.Account(
            id = "x", name = "Phantom", kind = app.hisaab.domain.AccountKind.BANK, institution = null, currency = "BDT",
            balanceTracking = true, createdAt = 0, archivedAt = null,
        )
        val provider = FakeAgentProvider(
            listOf(final("""{"tool":"post_transaction","args":{"messages":["1"],"account":"Phantom","amount":60,"kind":"EXPENSE"}}""")),
        )

        val outcome = s.agent(provider, extraAccounts = listOf(phantom)).process(batch)

        assertEquals(0, outcome.decided)
        assertEquals(CaptureStatus.QUEUED, s.status("a"))
        assertEquals(1, s.inbox.getById("a")?.attempts)
    }

    private fun failing(error: app.hisaab.llm.LlmError) = object : app.hisaab.agent.AgentProvider {
        override suspend fun complete(messages: List<app.hisaab.agent.ChatMessage>, maxTokens: Int): String =
            throw app.hisaab.llm.LlmException(error)
    }

    @Test
    fun `a reply the model can't finish counts as an attempt`() = runTest {
        val s = Store()
        val batch = s.queue(msg("a", "Paid Tk 60", 1_000))

        val outcome = s.agent(failing(app.hisaab.llm.LlmError.Decode("reply truncated at max_tokens"))).process(batch)

        assertEquals(0, outcome.decided)
        assertEquals(1, s.inbox.getById("a")?.attempts) // three of these and it becomes a question
    }

    @Test
    fun `a lost connection leaves the batch untouched for a retry`() = runTest {
        val s = Store()
        val batch = s.queue(msg("a", "Paid Tk 60", 1_000))

        kotlin.test.assertFailsWith<app.hisaab.llm.LlmException> {
            s.agent(failing(app.hisaab.llm.LlmError.Network("offline"))).process(batch)
        }

        assertEquals(0, s.inbox.getById("a")?.attempts)
    }

    @Test
    fun `the sender is prepared like the message before it leaves the phone`() = runTest {
        val s = Store()
        val batch = s.queue(msg("a", "Sent Tk 60", 1_000).copy(sender = "01712345678"))
        val provider = FakeAgentProvider(listOf(final("""{"tool":"skip","args":{"messages":["1"],"reason":"x"}}""")))

        s.agent(provider, prepareSender = { "[PHONE *5678]" }).process(batch)

        val prompt = provider.calls.single().joinToString("\n") { it.content }
        assertTrue("from [PHONE *5678]" in prompt)
        assertFalse("01712345678" in prompt)
    }

    @Test
    fun `only the most recent memories go into the prompt`() {
        val ctx = AgentContext(emptyList(), emptyList(), emptyList(), (0 until 150).map { "fact $it" })
        val prompt = CaptureAgentPrompt.system(
            app.hisaab.agent.ToolRegistry(emptyList(), captureDecisionDescriptors()), ctx, "2026-10-17",
        )
        assertTrue("fact 149" in prompt)
        assertFalse("- fact 0\n" in prompt)
        assertTrue("already listed" in prompt)
    }
}

