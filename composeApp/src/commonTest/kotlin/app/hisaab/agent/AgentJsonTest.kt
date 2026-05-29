// AgentJsonTest.kt
package app.hisaab.agent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

class AgentJsonTest {
    @Test fun `decodes an action envelope`() {
        val e = AgentJson.decodeEnvelope("""{"thought":"check","action":{"tool":"list_accounts","args":{}}}""")
        assertEquals("list_accounts", e.action?.tool)
        assertNull(e.final)
    }
    @Test fun `decodes a final envelope with proposed writes`() {
        val e = AgentJson.decodeEnvelope(
            """{"thought":"done","final":{"message":"ok","proposedWrites":[{"tool":"add_transaction","args":{"amount":500}}]}}""")
        assertNotNull(e.final)
        assertEquals("ok", e.final?.message)
        assertEquals("add_transaction", e.final?.proposedWrites?.single()?.tool)
    }
    @Test fun `strips json fences`() {
        val e = AgentJson.decodeEnvelope("```json\n{\"thought\":\"x\",\"final\":{\"message\":\"hi\"}}\n```")
        assertEquals("hi", e.final?.message)
    }
    @Test fun `throws on malformed`() {
        assertFailsWith<AgentDecodeException> { AgentJson.decodeEnvelope("not json at all") }
    }
}
