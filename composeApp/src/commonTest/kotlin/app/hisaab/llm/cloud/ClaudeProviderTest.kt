package app.hisaab.llm.cloud

import app.hisaab.domain.Category
import app.hisaab.domain.Direction
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaudeProviderTest {

    private val categories = listOf(
        Category("food", "Food & dining", null, null, null, true),
        Category("transport", "Transport", null, null, null, true),
    )

    private fun jsonHeaders() =
        headersOf(HttpHeaders.ContentType, "application/json")

    private val parsedJson =
        """{\"amount\":320.0,\"direction\":\"DEBIT\",\"merchant\":\"Shwapno\",\"categoryId\":\"food\",""" +
            """\"balanceAfter\":1008.0,\"confidence\":0.9,\"isFinancial\":true}"""

    private fun okEngine(onRequest: (body: String, beta: String?) -> Unit = { _, _ -> }) = MockEngine { request ->
        onRequest((request.body as io.ktor.http.content.TextContent).text, request.headers["anthropic-beta"])
        respond(
            content = """{"stop_reason":"end_turn","content":[""" +
                """{"type":"thinking","thinking":""},{"type":"text","text":"$parsedJson"}]}""",
            status = HttpStatusCode.OK,
            headers = jsonHeaders(),
        )
    }

    @Test
    fun `parse requests structured output from opus 5_5 and decodes the text block`() = runTest {
        var capturedBody = ""
        var capturedBeta: String? = null
        val provider = ClaudeProvider(
            HttpClient(okEngine { body, beta -> capturedBody = body; capturedBeta = beta }),
            apiKey = { "sk-ant-test" },
        )

        val result = provider.parse(
            ParseRequest(text = "Payment Tk 320 to Shwapno", senderHint = "bKash", categories = categories),
        )

        assertEquals(ProviderId.CLOUD_CLAUDE, provider.id)
        assertEquals(320.0, result.amount)
        assertEquals(Direction.DEBIT, result.direction)
        assertEquals("food", result.categoryId)
        assertTrue(capturedBody.contains("\"model\":\"claude-opus-5-5\""), capturedBody)
        assertTrue(capturedBody.contains("\"json_schema\""), capturedBody)
        assertTrue(capturedBody.contains("\"additionalProperties\":false"), capturedBody)
        assertTrue(capturedBody.contains("\"effort\":\"low\""), capturedBody)
        assertTrue(capturedBody.contains("\"fallbacks\":\"default\""), capturedBody)
        assertTrue(!capturedBody.contains("tool_choice"), "forced tool_choice 400s on Opus 5.5")
        assertEquals("server-side-fallback-2026-07-01", capturedBeta)
    }

    @Test
    fun `parse maps a refusal stop reason to Refused with its category`() = runTest {
        val engine = MockEngine {
            respond(
                """{"stop_reason":"refusal","stop_details":{"type":"refusal","category":"cyber"},"content":[]}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "k" })
        val ex = assertFailsWith<LlmException> { provider.parse(ParseRequest("x", null, categories)) }
        assertEquals(LlmError.Refused("cyber"), ex.error)
    }

    @Test
    fun `parse maps a max_tokens stop reason to a decode error`() = runTest {
        val engine = MockEngine {
            respond(
                """{"stop_reason":"max_tokens","content":[{"type":"text","text":"{\"amount\":3"}]}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "k" })
        val ex = assertFailsWith<LlmException> { provider.parse(ParseRequest("x", null, categories)) }
        assertTrue(ex.error is LlmError.Decode, "expected Decode, got ${ex.error}")
    }

    @Test
    fun `parse reads only the text after a fallback switch point`() = runTest {
        val engine = MockEngine {
            respond(
                """{"stop_reason":"end_turn","content":[{"type":"text","text":"{\"amount\":9"},""" +
                    """{"type":"fallback","from":{"model":"claude-opus-5-5"},"to":{"model":"claude-opus-4-8"}},""" +
                    """{"type":"text","text":"$parsedJson"}]}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "k" })
        assertEquals(320.0, provider.parse(ParseRequest("x", null, categories)).amount)
    }

    @Test
    fun `categorize asks opus 5_5 and returns an allowed category id`() = runTest {
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(
                """{"stop_reason":"end_turn","content":[{"type":"text","text":"{\"categoryId\":\"transport\"}"}]}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "k" })
        assertEquals("transport", provider.categorize("Pathao", categories))
        assertTrue(capturedBody.contains("\"model\":\"claude-opus-5-5\""), capturedBody)
    }

    @Test
    fun `verifyKey lists models with the key and passes on 200`() = runTest {
        var path = ""
        var method = ""
        var key: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method.value
            key = request.headers["x-api-key"]
            respond("""{"data":[]}""", HttpStatusCode.OK, jsonHeaders())
        }
        ClaudeProvider(HttpClient(engine), apiKey = { "sk-ant-test" }).verifyKey()
        assertEquals("/v1/models", path)
        assertEquals("GET", method)
        assertEquals("sk-ant-test", key)
    }

    @Test
    fun `verifyKey surfaces Anthropic's error message`() = runTest {
        val engine = MockEngine {
            respond(
                """{"type":"error","error":{"type":"invalid_request_error",""" +
                    """"message":"This API key is not scoped to a workspace"}}""",
                HttpStatusCode.BadRequest,
                jsonHeaders(),
            )
        }
        val ex = assertFailsWith<LlmException> {
            ClaudeProvider(HttpClient(engine), apiKey = { "k" }).verifyKey()
        }
        assertEquals(LlmError.ProviderError(400, "This API key is not scoped to a workspace"), ex.error)
    }

    @Test
    fun `verifyKey maps 401 to invalid key`() = runTest {
        val engine = MockEngine { respond("""{"type":"error"}""", HttpStatusCode.Unauthorized, jsonHeaders()) }
        val ex = assertFailsWith<LlmException> {
            ClaudeProvider(HttpClient(engine), apiKey = { "bad" }).verifyKey()
        }
        assertEquals(LlmError.InvalidKey, ex.error)
    }

    @Test
    fun `401 maps to invalid key`() = runTest {
        val engine = MockEngine { respond("unauthorized", HttpStatusCode.Unauthorized) }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "bad" })
        val ex = assertFailsWith<LlmException> {
            provider.parse(ParseRequest("x", null, categories))
        }
        assertEquals(LlmError.InvalidKey, ex.error)
    }

    @Test
    fun `429 maps to rate limited`() = runTest {
        val engine = MockEngine { respond("slow down", HttpStatusCode.TooManyRequests) }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "k" })
        val ex = assertFailsWith<LlmException> {
            provider.parse(ParseRequest("x", null, categories))
        }
        assertEquals(LlmError.RateLimited, ex.error)
    }

    @Test
    fun `isAvailable false when no key`() = runTest {
        val provider = ClaudeProvider(HttpClient(MockEngine { respond("", HttpStatusCode.OK) }), apiKey = { null })
        assertEquals(false, provider.isAvailable())
    }

    @Test
    fun `redact false sends raw text and redact true sends masked text`() = runTest {
        val rawText = "Send 500 to 01712345678 A/C 1234567890"
        var capturedBodyRaw = ""
        var capturedBodyRedacted = ""

        val okResponse = """{"stop_reason":"end_turn","content":[{"type":"text","text":"$parsedJson"}]}"""

        val engineRaw = MockEngine { request ->
            capturedBodyRaw = (request.body as io.ktor.http.content.TextContent).text
            respond(okResponse, HttpStatusCode.OK, jsonHeaders())
        }
        val engineRedacted = MockEngine { request ->
            capturedBodyRedacted = (request.body as io.ktor.http.content.TextContent).text
            respond(okResponse, HttpStatusCode.OK, jsonHeaders())
        }

        ClaudeProvider(HttpClient(engineRaw), apiKey = { "k" }, redact = { false })
            .parse(ParseRequest(rawText, null, categories))
        ClaudeProvider(HttpClient(engineRedacted), apiKey = { "k" }, redact = { true })
            .parse(ParseRequest(rawText, null, categories))

        assertTrue(capturedBodyRaw.contains("01712345678"), "raw body should contain unmasked phone")
        assertTrue(!capturedBodyRedacted.contains("01712345678"), "redacted body should not contain raw phone")
        assertTrue(capturedBodyRedacted.contains("[PHONE"), "redacted body should contain PHONE placeholder")
    }
}
