package app.hisaab.screens.agent

import app.hisaab.agent.AgentAvailability
import app.hisaab.agent.AgentRuntime
import app.hisaab.agent.WriteBatchCommitter
import app.hisaab.agent.buildAgentToolRegistry
import app.hisaab.agent.support.FakeAgentProvider
import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.ConversationRepository
import app.hisaab.data.InsightRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.AgentRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ViewModel turn-lifecycle tests.
 *
 * Dispatcher seam mirrors AutoCaptureViewModelTest:
 * - `StandardTestDispatcher` overrides `Dispatchers.Main` so all VM `scope.launch` coroutines
 *   are confined to the test scheduler.
 * - `ConversationRepository` receives the same test dispatcher for its flow collector so that
 *   `observeMessages` emissions are also driven by `advanceUntilIdle()`.
 * - `WriteBatchCommitter.apply()` reads `AccountRepository` / `CategoryRepository` via
 *   `Dispatchers.Default` (not overridable here); `advanceUntilIdle()` is called after
 *   `onApply()` to let the Default-thread work complete and queue its resumption on the
 *   test scheduler before assertions run. The test-scope timeout (default 10 s) guards
 *   against any genuine hang.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun runtime(
        db: HisaabDatabase,
        provider: FakeAgentProvider?,
        consented: Boolean,
    ): AgentRuntime {
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val committer = WriteBatchCommitter(
            db,
            AccountRepository(db),
            CategoryRepository(db),
            PersonRepository(db),
            txns,
            LendBorrowRepository(db, txns),
        )
        return AgentRuntime(
            registry = buildAgentToolRegistry(
                AccountRepository(db),
                CategoryRepository(db),
                MerchantRepository(db),
                txns,
                InsightRepository(db),
                PersonRepository(db),
            ),
            provider = provider,
            isConsented = { consented },
            accountNames = { "Cash" },
            categoryNames = { "Food" },
            committer = committer,
            todayIso = { "2026-05-30" },
        )
    }

    /**
     * Build a VM with the test dispatcher injected into ConversationRepository, mirroring how
     * AutoCaptureViewModelTest injects the dispatcher into CaptureConfigRepository.
     * The injected dispatcher ensures observeMessages flow emissions stay on the test scheduler.
     */
    private fun vm(
        db: HisaabDatabase,
        rt: AgentRuntime,
    ): AgentViewModel {
        val conversationRepo = ConversationRepository(db, flowDispatcher = dispatcher)
        return AgentViewModel(
            conversationRepo = conversationRepo,
            runtime = rt,
            setConsent = {},
        )
    }

    @Test
    fun `send proposes writes and persists user and assistant messages`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        AccountRepository(db).add("Cash", AccountKind.CASH, null)

        val provider = FakeAgentProvider(
            listOf(
                """{"thought":"t","final":{"message":"logged","proposedWrites":[{"tool":"add_transaction","args":{"account":"Cash","amount":500,"kind":"EXPENSE"}}]}}""",
            ),
        )
        val rt = runtime(db, provider, consented = true)
        val v = vm(db, rt)

        advanceUntilIdle()

        v.onInputChange("spent 500")
        v.onSend()
        advanceUntilIdle()

        val state = v.state.value
        assertEquals(1, state.review.size, "Expected 1 proposed write in review")
        assertNull(state.error)

        // messages are observed via the injected dispatcher — wait for the flow emission
        val msgs = v.state.first { it.messages.size >= 2 }.messages
        assertTrue(msgs.any { it.role == AgentRole.USER }, "Expected USER message")
        assertTrue(msgs.any { it.role == AgentRole.ASSISTANT }, "Expected ASSISTANT message")
    }

    @Test
    fun `apply commits and records applied summary then clears review`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        AccountRepository(db).add("Cash", AccountKind.CASH, null)

        val provider = FakeAgentProvider(
            listOf(
                """{"thought":"t","final":{"message":"logged","proposedWrites":[{"tool":"add_transaction","args":{"account":"Cash","amount":500,"kind":"EXPENSE"}}]}}""",
            ),
        )
        val rt = runtime(db, provider, consented = true)
        val v = vm(db, rt)

        advanceUntilIdle()

        v.onInputChange("spent 500")
        v.onSend()
        advanceUntilIdle()

        assertEquals(1, v.state.value.review.size)

        v.onApply()
        // Wait for the apply coroutine to complete. It suspends on Dispatchers.Default inside
        // WriteBatchCommitter; once the Default thread queues the resumption on the test
        // scheduler, advanceUntilIdle processes it and the state update runs.
        val finalState = v.state.first { it.review.isEmpty() }

        assertTrue(finalState.review.isEmpty(), "Review should be cleared after apply")
        assertEquals("Saved.", finalState.confirmation)
        assertNull(finalState.error)

        // transaction committed
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val recentTxns = txns.observeRecent().first()
        assertEquals(1, recentTxns.size, "Expected 1 committed transaction")

        // appliedSummary recorded on the assistant message — wait for the flow emission
        val msgs = v.state.first { m -> m.messages.any { it.appliedSummary != null } }.messages
        val assistantMsg = msgs.firstOrNull { it.role == AgentRole.ASSISTANT }
        assertNotNull(assistantMsg, "Expected an ASSISTANT message")
        assertNotNull(assistantMsg.appliedSummary, "Expected appliedSummary to be set")
        assertEquals(1, assistantMsg.appliedSummary.transactionsAdded)
    }

    @Test
    fun `gate blocks when not consented`() = runTest {
        val db = TestDatabase.create()

        val provider = FakeAgentProvider(emptyList())
        val rt = runtime(db, provider, consented = false)
        val v = vm(db, rt)

        advanceUntilIdle()

        v.onInputChange("spent 500")
        v.onSend()
        advanceUntilIdle()

        val state = v.state.value
        assertTrue(state.gate is AgentAvailability.NeedsConsent, "Expected gate to be NeedsConsent")
        // no assistant message — state.messages observed via injected dispatcher
        val msgs = v.state.first { it.messages.isNotEmpty() || it.gate != null }.messages
        assertTrue(msgs.none { it.role == AgentRole.ASSISTANT }, "No assistant message should exist")
    }

    @Test
    fun `toggle excludes a row from apply`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        AccountRepository(db).add("Cash", AccountKind.CASH, null)

        val provider = FakeAgentProvider(
            listOf(
                """{"thought":"t","final":{"message":"logged","proposedWrites":[{"tool":"add_transaction","args":{"account":"Cash","amount":100,"kind":"EXPENSE"}},{"tool":"add_transaction","args":{"account":"Cash","amount":200,"kind":"EXPENSE"}}]}}""",
            ),
        )
        val rt = runtime(db, provider, consented = true)
        val v = vm(db, rt)

        advanceUntilIdle()

        v.onInputChange("two expenses")
        v.onSend()
        advanceUntilIdle()

        assertEquals(2, v.state.value.review.size)

        // exclude index 1 — only the write at index 0 should be committed
        v.onToggleInclude(1)
        v.onApply()
        // wait for apply to complete and state to converge
        v.state.first { it.review.isEmpty() }

        // only the included write (index 0) should be committed
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val recentTxns = txns.observeRecent().first()
        assertEquals(1, recentTxns.size, "Only the included write should be committed")
        assertTrue(v.state.value.review.isEmpty(), "Review should be cleared after apply")
    }

    @Test
    fun `error path surfaces nothing saved`() = runTest {
        val db = TestDatabase.create()

        // empty queue → FakeAgentProvider exhausted → throws on complete
        val provider = FakeAgentProvider(emptyList())
        val rt = runtime(db, provider, consented = true)
        val v = vm(db, rt)

        advanceUntilIdle()

        v.onInputChange("spent 500")
        v.onSend()
        advanceUntilIdle()

        val state = v.state.value
        assertNotNull(state.error, "Expected error to be set")
        assertEquals(false, state.inFlight)

        // verify no assistant message persisted via repository
        val conversationRepo = ConversationRepository(db, flowDispatcher = dispatcher)
        val cid = conversationRepo.latestConversationId()
        assertNotNull(cid, "Expected a conversation to exist")
        val msgs = conversationRepo.observeMessages(cid).first()
        assertTrue(msgs.none { it.role == AgentRole.ASSISTANT }, "No assistant message should be persisted on error")
    }

    @Test
    fun `onDismissBanner clears error and confirmation`() = runTest {
        val db = TestDatabase.create()
        // empty queue → first send fails → error set
        val provider = FakeAgentProvider(emptyList())
        val rt = runtime(db, provider, consented = true)
        val v = vm(db, rt)
        advanceUntilIdle()

        v.onInputChange("spent 500")
        v.onSend()
        advanceUntilIdle()
        assertNotNull(v.state.value.error, "Expected error after a failed send")

        v.onDismissBanner()
        advanceUntilIdle()
        assertNull(v.state.value.error, "onDismissBanner should clear error")
        assertNull(v.state.value.confirmation, "onDismissBanner should clear confirmation")
    }

    @Test
    fun `a new send clears a prior Saved confirmation`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        AccountRepository(db).add("Cash", AccountKind.CASH, null)

        // one good response for the first turn; the queue is exhausted for the second send
        val provider = FakeAgentProvider(
            listOf(
                """{"thought":"t","final":{"message":"logged","proposedWrites":[{"tool":"add_transaction","args":{"account":"Cash","amount":500,"kind":"EXPENSE"}}]}}""",
            ),
        )
        val rt = runtime(db, provider, consented = true)
        val v = vm(db, rt)
        advanceUntilIdle()

        // first turn + apply → confirmation = "Saved."
        v.onInputChange("spent 500")
        v.onSend()
        advanceUntilIdle()
        v.onApply()
        val saved = v.state.first { it.confirmation == "Saved." }
        assertEquals("Saved.", saved.confirmation)

        // second send: provider exhausted → run() throws → error set AND the stale confirmation cleared
        v.onInputChange("again")
        v.onSend()
        advanceUntilIdle()
        val after = v.state.value
        assertNotNull(after.error, "Expected error on the failed second send")
        assertNull(after.confirmation, "A new send must clear the prior 'Saved.' confirmation")
    }
}
