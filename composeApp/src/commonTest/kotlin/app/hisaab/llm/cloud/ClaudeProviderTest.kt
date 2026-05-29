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

    @Test
    fun `parse sends tool_use request and decodes tool input`() = runTest {
        var capturedBody = ""
        var capturedAuth: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            capturedAuth = request.headers["x-api-key"]
            respond(
                content = """
                  {"content":[
                     {"type":"tool_use","name":"record_transaction",
                      "input":{"amount":320.0,"direction":"DEBIT","merchant":"Shwapno",
                               "categoryId":"food","balanceAfter":1008.0,"refNo":null,
                               "confidence":0.9,"isFinancial":true}}]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val provider = ClaudeProvider(HttpClient(engine), apiKey = { "sk-ant-test" })

        val result = provider.parse(
            ParseRequest(text = "Payment Tk 320 to Shwapno", senderHint = "bKash", categories = categories),
        )

        assertEquals(ProviderId.CLOUD_CLAUDE, provider.id)
        assertEquals(320.0, result.amount)
        assertEquals(Direction.DEBIT, result.direction)
        assertEquals("food", result.categoryId)
        assertEquals("sk-ant-test", capturedAuth)
        assertTrue(capturedBody.contains("\"tools\""), capturedBody)
        assertTrue(capturedBody.contains("input_schema"), capturedBody)
        assertTrue(capturedBody.contains("claude"), capturedBody)
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
}
