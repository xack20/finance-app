package app.hisaab.llm.cloud
import app.hisaab.agent.ChatMessage
import app.hisaab.agent.Role
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

class GeminiCompleteTest {
    @Test fun `complete returns the candidate text`() = runTest {
        val body = """{"candidates":[{"content":{"parts":[{"text":"{\"thought\":\"t\",\"final\":{\"message\":\"hi\"}}"}]}}]}"""
        val engine = MockEngine { respond(body, HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }
        val provider = GeminiProvider(HttpClient(engine), apiKey = { "k" })
        val out = provider.complete(listOf(ChatMessage(Role.SYSTEM, "SYS"), ChatMessage(Role.USER, "hi")))
        assertEquals("{\"thought\":\"t\",\"final\":{\"message\":\"hi\"}}", out)
    }
}
