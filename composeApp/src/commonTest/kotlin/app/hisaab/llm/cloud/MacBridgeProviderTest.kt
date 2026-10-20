package app.hisaab.llm.cloud

import app.hisaab.agent.ChatMessage
import app.hisaab.agent.Role
import app.hisaab.bridge.BridgeRequest
import app.hisaab.bridge.BridgeResponse
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MacBridgeProviderTest {

    /** Plain-JSON stand-in for the libsodium codec (no native crypto in JVM unit tests). */
    private val plain = object : BridgeCodec {
        override fun seal(request: BridgeRequest) = Json.encodeToString(BridgeRequest.serializer(), request)
        override fun open(body: String) = Json.decodeFromString(BridgeResponse.serializer(), body)
    }

    private var sent: BridgeRequest? = null
    private var url = ""

    private fun provider(
        address: String? = "192.168.1.5",
        status: HttpStatusCode = HttpStatusCode.OK,
        reply: (BridgeRequest) -> BridgeResponse = { BridgeResponse(it.id, text = "ok") },
    ) = MacBridgeProvider(
        HttpClient(MockEngine { request ->
            url = request.url.toString()
            val body = (request.body as io.ktor.http.content.TextContent).text
            val req = plain.run { Json.decodeFromString(BridgeRequest.serializer(), body) }
            sent = req
            respond(Json.encodeToString(BridgeResponse.serializer(), reply(req)), status)
        }),
        address = { address },
        codec = { plain },
        now = { 1_000L },
    )

    @Test
    fun `the conversation goes to the Mac and Claude's reply comes back`() = runTest {
        val reply = provider { BridgeResponse(it.id, text = """{"final":{}}""") }.complete(
            listOf(
                ChatMessage(Role.SYSTEM, "SYS"),
                ChatMessage(Role.USER, "batch"),
                ChatMessage(Role.ASSISTANT, "lookup"),
                ChatMessage(Role.TOOL, "result"),
            ),
            maxTokens = 16_000,
        )

        assertEquals("""{"final":{}}""", reply)
        assertEquals("http://192.168.1.5:47800/v1/bridge", url)
        val req = requireNotNull(sent)
        assertEquals("SYS", req.system)
        assertEquals(listOf("user", "assistant", "user"), req.turns.map { it.role })
        assertEquals(1_000L, req.sentAtMs)
    }

    @Test
    fun `an address with its own port is used as given`() = runTest {
        provider(address = "my-mac.tailnet.ts.net:5000").complete(listOf(ChatMessage(Role.USER, "x")), 100)
        assertEquals("http://my-mac.tailnet.ts.net:5000/v1/bridge", url)
    }

    @Test
    fun `the subscription limit is a rate limit, so reading waits instead of failing`() = runTest {
        val e = assertFailsWith<LlmException> {
            provider { BridgeResponse(it.id, error = "usage limit reached", limitReached = true) }
                .complete(listOf(ChatMessage(Role.USER, "x")), 100)
        }
        assertEquals(LlmError.RateLimited, e.error)
    }

    @Test
    fun `a Mac paired with another recovery phrase is reported as a rejected key`() = runTest {
        val e = assertFailsWith<LlmException> {
            provider(status = HttpStatusCode.Unauthorized).complete(listOf(ChatMessage(Role.USER, "x")), 100)
        }
        assertEquals(LlmError.InvalidKey, e.error)
    }

    @Test
    fun `an unreachable Mac is a network error`() = runTest {
        val p = MacBridgeProvider(
            HttpClient(MockEngine { throw IOException("connect timed out") }),
            address = { "192.168.1.5" }, codec = { plain },
        )
        val e = assertFailsWith<LlmException> { p.complete(listOf(ChatMessage(Role.USER, "x")), 100) }
        assertTrue(e.error is LlmError.Network)
    }

    @Test
    fun `validate pings the Mac without asking Claude`() = runTest {
        provider().verifyKey()
        assertEquals(true, sent?.ping)

        val missing = assertFailsWith<LlmException> {
            provider { BridgeResponse(it.id, error = "Claude Code isn't installed on this Mac") }.verifyKey()
        }
        assertTrue((missing.error as LlmError.ProviderError).detail.contains("Claude Code"))
    }
}
