package app.hisaab.data
import app.hisaab.agent.AppliedSummary
import app.hisaab.agent.ProposedWrite
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AgentRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationRepositoryTest {
    @Test fun `append and observe a full turn round-trips proposedWrites + appliedSummary`() = runTest {
        val db = TestDatabase.create()
        val repo = ConversationRepository(db)
        val convId = repo.createConversation(title = "Lunch")
        repo.appendUserMessage(convId, "spent 500 on lunch")
        repo.appendAssistantMessage(
            conversationId = convId,
            content = "Logged 500 for lunch.",
            proposedWrites = listOf(ProposedWrite("add_transaction",
                buildJsonObject { put("account", "Cash"); put("amount", 500.0); put("kind", "EXPENSE") })),
            appliedSummary = AppliedSummary(transactionsAdded = 1),
        )
        val msgs = repo.observeMessages(convId).first()
        assertEquals(2, msgs.size)
        val user = msgs.first(); val assistant = msgs.last()
        assertEquals(AgentRole.USER, user.role)
        assertTrue(user.proposedWrites.isEmpty()); assertNull(user.appliedSummary)
        assertEquals(AgentRole.ASSISTANT, assistant.role)
        assertEquals("add_transaction", assistant.proposedWrites.single().tool)
        assertEquals(1, assistant.appliedSummary?.transactionsAdded)
    }

    @Test fun `conversations are listed newest-updated first and latest is resolvable`() = runTest {
        val db = TestDatabase.create()
        var t = 0L
        val repo = ConversationRepository(db, now = { ++t })   // strictly increasing → deterministic ordering
        val a = repo.createConversation("A")          // A.updated_at = 1
        repo.createConversation("B")                  // B.updated_at = 2
        repo.appendUserMessage(a, "touch A so it floats to the top")  // A.updated_at = 3
        val convos = repo.observeConversations().first()
        assertEquals(2, convos.size)
        assertEquals(a, convos.first().id)            // A updated last (3 > 2)
        assertEquals(a, repo.latestConversationId())
    }
}
