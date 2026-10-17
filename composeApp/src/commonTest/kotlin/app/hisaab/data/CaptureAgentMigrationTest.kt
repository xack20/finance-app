package app.hisaab.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Migration 10.sqm: the rule-based capture's leftovers are handed to the capture agent. */
class CaptureAgentMigrationTest {

    private fun row(id: String, status: CaptureStatus) = CandidateTransaction(
        id = id, receivedAt = 1L, channel = CaptureChannel.SMS, sender = "bKash", rawBody = "b-$id",
        dedupHash = "h-$id", status = status, confidence = null, parsedBy = null, model = null,
        parseError = "llm_not_financial", amount = null, direction = null, currency = "BDT", balanceAfter = null,
        refNo = null, proposedAccountId = null, proposedCategoryId = null, proposedMerchant = null, createdAt = 1L,
    )

    @Test
    fun `old Review items are handed to the agent and sender mappings become memories`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HisaabDatabase.Schema.migrate(driver, 1, 10) // the schema a phone has before this migration
        val db = HisaabDatabase(driver)
        val inbox = CaptureInboxRepository(db)
        inbox.insertCandidate(row("waiting", CaptureStatus.PENDING))
        inbox.insertCandidate(row("posted", CaptureStatus.AUTO_POSTED))
        val card = AccountRepository(db).add("City Bank card", AccountKind.CARD, institution = null)
        driver.execute(
            null,
            "INSERT INTO sender_registry(id, sender_id, display_name, bank_type, is_financial, account_id, created_at) " +
                "VALUES ('s1', 'alerts@citybank.com', 'City', 'OTHER', 1, '$card', 1)",
            0,
        )

        HisaabDatabase.Schema.migrate(driver, 10, 11)

        assertEquals(CaptureStatus.QUEUED, inbox.getById("waiting")?.status)
        assertEquals(CaptureStatus.AUTO_POSTED, inbox.getById("posted")?.status)
        val facts = db.agentMemoryQueriesQueries.observeAll().executeAsList().map { it.fact }
        assertEquals(1, facts.size)
        assertTrue("alerts@citybank.com" in facts.single() && "City Bank card" in facts.single(), facts.single())
    }
}
