package app.hisaab.agent
import app.hisaab.agent.support.FakeAgentProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoWriteInLoopTest {
    @Test fun `agent loop collects proposed writes without touching any database`() = runTest {
        // AgentLoop has NO db dependency: a multi-write turn completes and returns inert ProposedWrites.
        val registry = ToolRegistry(readTools = emptyList(), writeDescriptors = agentWriteDescriptors())
        val provider = FakeAgentProvider(listOf(
            """{"thought":"log both","final":{"message":"done","proposedWrites":[{"tool":"add_transaction","args":{"account":"Cash","amount":500,"kind":"EXPENSE"}},{"tool":"transfer","args":{"fromAccount":"Bank","toAccount":"Cash","amount":1000}}]}}""",
        ))
        val result = AgentLoop(provider, registry, systemPrompt = "SYS", maxIterations = 6)
            .run(history = emptyList(), userMessage = "spent 500 and moved 1000")
        assertEquals("done", result.finalMessage)
        assertEquals(2, result.proposedWrites.size)
        assertTrue(result.proposedWrites.all { it.tool == "add_transaction" || it.tool == "transfer" })
    }
}
