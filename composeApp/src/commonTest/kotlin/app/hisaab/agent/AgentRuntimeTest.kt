package app.hisaab.agent
import app.hisaab.agent.support.FakeAgentProvider
import app.hisaab.data.*
import app.hisaab.data.support.TestDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

class AgentRuntimeTest {
    private fun runtime(db: app.hisaab.db.HisaabDatabase, provider: FakeAgentProvider?, consented: Boolean): AgentRuntime {
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val committer = WriteBatchCommitter(db, AccountRepository(db), CategoryRepository(db), PersonRepository(db), txns, LendBorrowRepository(db, txns))
        return AgentRuntime(
            registry = buildAgentToolRegistry(AccountRepository(db), CategoryRepository(db), MerchantRepository(db), txns, InsightRepository(db), PersonRepository(db)),
            agentProvider = { provider },
            isConsented = { consented },
            accountNames = { "Cash" }, categoryNames = { "Food" },
            committer = committer, todayIso = { "2026-05-30" },
        )
    }
    @Test fun `needs consent when not consented`() = runTest {
        assertTrue(runtime(TestDatabase.create(), FakeAgentProvider(emptyList()), false).availability() is AgentAvailability.NeedsConsent)
    }
    @Test fun `unavailable when no provider`() = runTest {
        assertTrue(runtime(TestDatabase.create(), null, true).availability() is AgentAvailability.Unavailable)
    }
    @Test fun `ready when consented and provider present`() = runTest {
        assertEquals(AgentAvailability.Ready, runtime(TestDatabase.create(), FakeAgentProvider(emptyList()), true).availability())
    }
    @Test fun `run returns proposed writes`() = runTest {
        val p = FakeAgentProvider(listOf(
            """{"thought":"t","final":{"message":"logged","proposedWrites":[{"tool":"add_transaction","args":{"account":"Cash","amount":500,"kind":"EXPENSE"}}]}}"""))
        val res = runtime(TestDatabase.create(), p, true).run(emptyList(), "spent 500")
        assertEquals("logged", res.finalMessage); assertEquals(1, res.proposedWrites.size)
    }
    @Test fun `apply commits writes`() = runTest {
        val db = TestDatabase.create(); CategoryRepository(db).ensureDefaults()
        AccountRepository(db).add("Cash", app.hisaab.domain.AccountKind.CASH, null)
        val s = runtime(db, FakeAgentProvider(emptyList()), true).apply(listOf(
            ProposedWrite("add_transaction", buildJsonObject { put("account","Cash"); put("amount",500.0); put("kind","EXPENSE") })))
        assertEquals(1, s.transactionsAdded)
    }
}
