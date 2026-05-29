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

class OpenAiProviderTest {

    private val categories = listOf(
        Category("food", "Food & dining", null, null, null, true),
        Category("transport", "Transport", null, null, null, true),
    )

    @Test
    fun `parse sends json_schema and decodes message content`() = runTest {
        var capturedBody = ""
        var capturedAuth: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond(
                content = """
                  {"choices":[{"message":{"role":"assistant",
                     "content":"{\"amount\":120.0,\"direction\":\"DEBIT\",\"merchant\":\"Uber\",\"categoryId\":\"transport\",\"balanceAfter\":null,\"refNo\":null,\"confidence\":0.88,\"isFinancial\":true}"}}]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val provider = OpenAiProvider(HttpClient(engine), apiKey = { "sk-oai" })

        val r = provider.parse(ParseRequest("Uber ride Tk 120", null, categories))

        assertEquals(ProviderId.CLOUD_OPENAI, provider.id)
        assertEquals(120.0, r.amount)
        assertEquals(Direction.DEBIT, r.direction)
        assertEquals("transport", r.categoryId)
        assertEquals("Bearer sk-oai", capturedAuth)
        assertTrue(capturedBody.contains("json_schema"), capturedBody)
        assertTrue(capturedBody.contains("gpt-4o-mini"), capturedBody)
    }

    @Test
    fun `500 maps to retryable provider error`() = runTest {
        val engine = MockEngine { respond("server error", HttpStatusCode.InternalServerError) }
        val provider = OpenAiProvider(HttpClient(engine), apiKey = { "k" })
        val ex = assertFailsWith<LlmException> { provider.parse(ParseRequest("x", null, categories)) }
        assertTrue(ex.error is LlmError.ProviderError && ex.error.retryable)
    }
}
