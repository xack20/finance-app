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

class OpenAiCompleteTest {
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `complete maps roles, requests json_object, and returns message content`() = runTest {
        var body = ""
        var auth: String? = null
        val engine = MockEngine { request ->
            body = (request.body as io.ktor.http.content.TextContent).text
            auth = request.headers["Authorization"]
            respond(
                content = """{"choices":[{"message":{"content":"{\"thought\":\"t\",\"final\":{\"message\":\"ok\"}}"}}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val provider = OpenAiProvider(HttpClient(engine), apiKey = { "sk-openai-test" })

        val out = provider.complete(
            listOf(
                ChatMessage(Role.SYSTEM, "SYS"),
                ChatMessage(Role.USER, "spent 500"),
                ChatMessage(Role.ASSISTANT, "prev"),
            ),
        )

        assertEquals("""{"thought":"t","final":{"message":"ok"}}""", out)
        assertTrue(body.contains("\"role\":\"system\""), "SYSTEM turn mapped to system role")
        assertTrue(body.contains("\"role\":\"assistant\""), "ASSISTANT turn present")
        assertTrue(body.contains("\"json_object\""), "json_object response_format requested")
        assertEquals("Bearer sk-openai-test", auth)
    }

    @Test
    fun `complete throws InvalidKey when no api key`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK, jsonHeaders()) }
        val provider = OpenAiProvider(HttpClient(engine), apiKey = { null })
        assertFailsWith<LlmException> { provider.complete(listOf(ChatMessage(Role.USER, "hi"))) }
    }

    @Test
    fun `complete maps an http error to LlmException`() = runTest {
        val engine = MockEngine { respond("server error", HttpStatusCode.InternalServerError, jsonHeaders()) }
        val provider = OpenAiProvider(HttpClient(engine), apiKey = { "k" })
        assertFailsWith<LlmException> { provider.complete(listOf(ChatMessage(Role.USER, "hi"))) }
    }
}
