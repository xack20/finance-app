package app.hisaab.llm.cloud

import app.hisaab.agent.ChatMessage
import app.hisaab.agent.Role
import app.hisaab.llm.LlmException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaudeCompleteTest {
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `complete maps roles and returns concatenated text blocks`() = runTest {
        var body = ""
        var version: String? = null
        var apiKeyHeader: String? = null
        val engine = MockEngine { request ->
            body = (request.body as io.ktor.http.content.TextContent).text
            version = request.headers["anthropic-version"]
            apiKeyHeader = request.headers["x-api-key"]
            respond(
                content = """{"content":[{"type":"text","text":"{\"thought\":\"t\",\"final\":{\"message\":\"ok\"}}"}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "sk-ant-test" })

        val out = provider.complete(
            listOf(
                ChatMessage(Role.SYSTEM, "SYS"),
                ChatMessage(Role.USER, "spent 500"),
                ChatMessage(Role.ASSISTANT, "prev"),
            ),
        )

        assertEquals("""{"thought":"t","final":{"message":"ok"}}""", out)
        // SYSTEM goes in the top-level system field, not the messages array.
        assertTrue(body.contains("\"system\":\"SYS\""), "system field should carry the SYSTEM turn")
        assertTrue(body.contains("\"role\":\"user\""), "USER turn present")
        assertTrue(body.contains("\"role\":\"assistant\""), "ASSISTANT turn present")
        assertEquals("2023-06-01", version)
        assertEquals("sk-ant-test", apiKeyHeader)
    }

    @Test
    fun `complete throws InvalidKey when no api key`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK, jsonHeaders()) }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { null })
        assertFailsWith<LlmException> { provider.complete(listOf(ChatMessage(Role.USER, "hi"))) }
    }

    @Test
    fun `complete maps an http error to LlmException`() = runTest {
        val engine = MockEngine { respond("rate limited", HttpStatusCode.TooManyRequests, jsonHeaders()) }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "k" })
        assertFailsWith<LlmException> { provider.complete(listOf(ChatMessage(Role.USER, "hi"))) }
    }
}
