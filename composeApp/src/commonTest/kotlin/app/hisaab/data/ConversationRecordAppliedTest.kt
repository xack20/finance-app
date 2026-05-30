package app.hisaab.data
import app.hisaab.agent.AppliedSummary
import app.hisaab.data.support.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRecordAppliedTest {
    @Test fun `recordApplied attaches a summary to an existing assistant message`() = runTest {
        val db = TestDatabase.create()
        val repo = ConversationRepository(db)
        val c = repo.createConversation("t")
        val mid = repo.appendAssistantMessage(c, "ok", proposedWrites = emptyList(), appliedSummary = null)
        repo.recordApplied(mid, AppliedSummary(transactionsAdded = 2))
        val msg = repo.observeMessages(c).first().single { it.id == mid }
        assertEquals(2, msg.appliedSummary?.transactionsAdded)
    }
}
