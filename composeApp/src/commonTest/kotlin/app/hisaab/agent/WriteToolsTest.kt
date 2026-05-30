package app.hisaab.agent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class WriteToolsTest {
    @Test fun `descriptors advertise the six write tools`() {
        val names = agentWriteDescriptors().map { it.name }.toSet()
        assertTrue(names.containsAll(setOf(
            "create_account", "create_category", "add_transaction",
            "record_lend_borrow", "transfer", "record_card_payment")))
    }
    @Test fun `parses add_transaction args`() {
        val w = ProposedWrite("add_transaction", buildJsonObject {
            put("account", "Cash"); put("amount", 500.0); put("kind", "EXPENSE")
            put("category", "Food"); put("notes", "lunch")
        })
        val intent = WriteIntent.parse(w)
        assertTrue(intent is WriteIntent.AddTransaction)
        intent as WriteIntent.AddTransaction
        assertEquals("Cash", intent.account); assertEquals(500.0, intent.amount)
        assertEquals("EXPENSE", intent.kind); assertEquals("Food", intent.category)
    }
    @Test fun `parses transfer args`() {
        val w = ProposedWrite("transfer", buildJsonObject {
            put("fromAccount", "Bank"); put("toAccount", "Cash"); put("amount", 1000.0)
        })
        assertTrue(WriteIntent.parse(w) is WriteIntent.Transfer)
    }
    @Test fun `unknown tool parses to null`() {
        assertNull(WriteIntent.parse(ProposedWrite("frobnicate", buildJsonObject {})))
    }
    @Test fun `missing required field parses to null`() {
        assertNull(WriteIntent.parse(ProposedWrite("add_transaction", buildJsonObject { put("account", "Cash") })))
    }
}
