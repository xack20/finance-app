// AgentPromptsTest.kt
package app.hisaab.agent
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentPromptsTest {
    private val reg = ToolRegistry(
        readTools = listOf(object : ReadTool {
            override val name = "list_accounts"; override val description = "List accounts"
            override val paramsDoc = "{}"; override suspend fun execute(args: JsonObject) = "[]"
        }),
        writeDescriptors = listOf(WriteDescriptor("add_transaction", "Add a transaction", "{\"amount\":number}")),
    )
    @Test fun `prompt advertises tools, protocol, and context`() {
        val p = AgentPrompts.system(reg, accounts = "Cash, bKash", categories = "Food, Rent",
            todayIso = "2026-05-30", language = "English/Bengali/Banglish")
        assertTrue("list_accounts" in p)
        assertTrue("add_transaction" in p)
        assertTrue("\"action\"" in p && "\"final\"" in p)       // protocol described
        assertTrue("2026-05-30" in p)
        assertTrue("Cash, bKash" in p)
        assertTrue("Food, Rent" in p)
    }
}
