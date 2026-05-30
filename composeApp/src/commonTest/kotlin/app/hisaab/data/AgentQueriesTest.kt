package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentQueriesTest {
    @Test fun `conversation and message round-trip at the query level`() {
        val db = TestDatabase.create()
        val q = db.agentQueriesQueries
        q.insertConversation(id = "c1", title = "First", created_at = 100L, updated_at = 100L)
        q.insertMessage(id = "m1", conversation_id = "c1", role = "USER", content = "hi",
            proposed_writes = null, applied_summary = null, created_at = 110L)
        q.insertMessage(id = "m2", conversation_id = "c1", role = "ASSISTANT", content = "hello",
            proposed_writes = "[]", applied_summary = null, created_at = 120L)

        val convos = q.observeConversations().executeAsList()
        assertEquals(1, convos.size)
        assertEquals("First", convos.single().title)

        val msgs = q.observeMessages("c1").executeAsList()
        assertEquals(2, msgs.size)
        assertEquals("hi", msgs.first().content)
        assertEquals("ASSISTANT", msgs.last().role)
    }
}
