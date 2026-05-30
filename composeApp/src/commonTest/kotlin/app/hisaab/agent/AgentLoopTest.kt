package app.hisaab.agent

import app.hisaab.agent.support.FakeAgentProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentLoopTest {
    private fun reg(read: List<ReadTool> = emptyList()) = ToolRegistry(
        readTools = read,
        writeDescriptors = listOf(WriteDescriptor("add_transaction", "add txn", "{}")),
    )
    private fun loop(provider: FakeAgentProvider, registry: ToolRegistry) =
        AgentLoop(provider, registry, systemPrompt = "SYS", maxIterations = 6)

    private class CountingRead(override val name: String, private val out: String) : ReadTool {
        override val description = "d"; override val paramsDoc = "{}"
        var calls = 0
        override suspend fun execute(args: JsonObject): String { calls++; return out }
    }

    @Test fun `single final returns the message`() = runTest {
        val p = FakeAgentProvider(listOf("""{"thought":"t","final":{"message":"hello"}}"""))
        val r = loop(p, reg()).run(history = emptyList(), userMessage = "hi")
        assertEquals("hello", r.finalMessage)
        assertTrue(r.proposedWrites.isEmpty())
        assertEquals(1, p.callCount)
    }

    @Test fun `read action then final - tool executes and result feeds back`() = runTest {
        val tool = CountingRead("list_accounts", "[{\"name\":\"Cash\"}]")
        val p = FakeAgentProvider(listOf(
            """{"thought":"look","action":{"tool":"list_accounts","args":{}}}""",
            """{"thought":"ok","final":{"message":"you have Cash"}}""",
        ))
        val r = loop(p, reg(listOf(tool))).run(emptyList(), "what accounts?")
        assertEquals("you have Cash", r.finalMessage)
        assertEquals(1, tool.calls)
        assertTrue(p.calls[1].any { it.role == Role.TOOL && "Cash" in it.content })
    }

    @Test fun `unknown tool is rejected and fed back, then final`() = runTest {
        val p = FakeAgentProvider(listOf(
            """{"thought":"x","action":{"tool":"nope","args":{}}}""",
            """{"thought":"y","final":{"message":"recovered"}}""",
        ))
        val r = loop(p, reg()).run(emptyList(), "hi")
        assertEquals("recovered", r.finalMessage)
        assertTrue(p.calls[1].any { it.role == Role.TOOL && "unknown tool" in it.content.lowercase() })
    }

    @Test fun `malformed envelope triggers one repair then final`() = runTest {
        val p = FakeAgentProvider(listOf(
            "not json",
            """{"thought":"y","final":{"message":"fixed"}}""",
        ))
        val r = loop(p, reg()).run(emptyList(), "hi")
        assertEquals("fixed", r.finalMessage)
    }

    @Test fun `max iterations cap returns a graceful message`() = runTest {
        val tool = CountingRead("list_accounts", "[]")
        val act = """{"thought":"loop","action":{"tool":"list_accounts","args":{}}}"""
        val p = FakeAgentProvider(List(10) { act })   // never finalizes
        val r = loop(p, reg(listOf(tool))).run(emptyList(), "hi")
        assertTrue(r.cappedOut)
        assertEquals(6, p.callCount)                  // bounded
    }

    @Test fun `final proposedWrites are collected but no write is executed`() = runTest {
        val p = FakeAgentProvider(listOf(
            """{"thought":"t","final":{"message":"ready","proposedWrites":[{"tool":"add_transaction","args":{"amount":500}}]}}"""))
        val r = loop(p, reg()).run(emptyList(), "spent 500")
        assertEquals(1, r.proposedWrites.size)
        assertEquals("add_transaction", r.proposedWrites.single().tool)
    }

    @Test fun `write tool used as action is rejected with guidance`() = runTest {
        val p = FakeAgentProvider(listOf(
            """{"thought":"x","action":{"tool":"add_transaction","args":{}}}""",
            """{"thought":"y","final":{"message":"recovered"}}""",
        ))
        val r = loop(p, reg()).run(emptyList(), "spend 100")
        assertEquals("recovered", r.finalMessage)
        assertTrue(p.calls[1].any { it.role == Role.TOOL && "write tool" in it.content })
    }
}
