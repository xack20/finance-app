package app.hisaab.agent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentWriteCodecTest {
    @Test fun `proposed writes round-trip through JSON`() {
        val writes = listOf(
            ProposedWrite("add_transaction", buildJsonObject { put("account", "Cash"); put("amount", 500.0) }),
            ProposedWrite("transfer", buildJsonObject { put("fromAccount", "Bank"); put("toAccount", "Cash"); put("amount", 1000.0) }),
        )
        val encoded = AgentWriteCodec.encodeWrites(writes)
        assertTrue(encoded != null && encoded.contains("add_transaction"))
        val decoded = AgentWriteCodec.decodeWrites(encoded)
        assertEquals(2, decoded.size)
        assertEquals("add_transaction", decoded.first().tool)
        assertEquals("Cash", (decoded.first().args["account"] as JsonPrimitive).content)
    }
    @Test fun `empty writes encode to null and decode to empty`() {
        assertNull(AgentWriteCodec.encodeWrites(emptyList()))
        assertTrue(AgentWriteCodec.decodeWrites(null).isEmpty())
        assertTrue(AgentWriteCodec.decodeWrites("").isEmpty())
    }
    @Test fun `applied summary round-trips`() {
        val s = AppliedSummary(accountsCreated = 1, transactionsAdded = 2, transfers = 1)
        val encoded = AgentWriteCodec.encodeSummary(s)
        assertEquals(s, AgentWriteCodec.decodeSummary(encoded))
        assertNull(AgentWriteCodec.encodeSummary(null))
        assertNull(AgentWriteCodec.decodeSummary(null))
    }
    @Test fun `malformed json decodes safely`() {
        assertTrue(AgentWriteCodec.decodeWrites("not json").isEmpty())
        assertNull(AgentWriteCodec.decodeSummary("not json"))
    }
}
