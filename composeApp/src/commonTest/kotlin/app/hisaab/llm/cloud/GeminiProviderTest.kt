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

class GeminiProviderTest {

    private val categories = listOf(
        Category("food", "Food & dining", null, null, null, true),
        Category("salary", "Salary", null, null, null, true),
    )

    @Test
    fun `parse sends responseSchema and decodes candidate text`() = runTest {
        var capturedBody = ""
        var capturedUrl = ""
        val engine = MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            capturedUrl = request.url.toString()
            respond(
                content = """
                  {"candidates":[{"content":{"parts":[
                     {"text":"{\"amount\":50000.0,\"direction\":\"CREDIT\",\"merchant\":null,\"categoryId\":\"salary\",\"balanceAfter\":52000.0,\"refNo\":null,\"confidence\":0.92,\"isFinancial\":true}"}
                  ]}}]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val provider = GeminiProvider(HttpClient(engine), apiKey = { "g-key" })

        val r = provider.parse(ParseRequest("Salary credited Tk 50000", "BANK", categories))

        assertEquals(ProviderId.CLOUD_GEMINI, provider.id)
        assertEquals(50000.0, r.amount)
        assertEquals(Direction.CREDIT, r.direction)
        assertEquals("salary", r.categoryId)
        assertTrue(capturedUrl.contains("key=g-key"), capturedUrl)
        assertTrue(capturedBody.contains("responseSchema"), capturedBody)
        assertTrue(capturedBody.contains("application/json"), capturedBody)
    }

    @Test
    fun `403 maps to invalid key`() = runTest {
        val engine = MockEngine { respond("forbidden", HttpStatusCode.Forbidden) }
        val provider = GeminiProvider(HttpClient(engine), apiKey = { "bad" })
        val ex = assertFailsWith<LlmException> { provider.parse(ParseRequest("x", null, categories)) }
        assertEquals(LlmError.InvalidKey, ex.error)
    }
}
