package app.hisaab.llm

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudHttpTest {

    @Test
    fun `401 maps to invalid key`() {
        assertEquals(LlmError.InvalidKey, mapHttpError(HttpStatusCode.Unauthorized, ""))
    }

    @Test
    fun `403 maps to invalid key`() {
        assertEquals(LlmError.InvalidKey, mapHttpError(HttpStatusCode.Forbidden, ""))
    }

    @Test
    fun `429 maps to rate limited`() {
        assertEquals(LlmError.RateLimited, mapHttpError(HttpStatusCode.TooManyRequests, ""))
    }

    @Test
    fun `500 maps to retryable provider error`() {
        val e = mapHttpError(HttpStatusCode.InternalServerError, "down")
        assertTrue(e is LlmError.ProviderError && e.retryable)
    }

    @Test
    fun `400 maps to non-retryable provider error`() {
        val e = mapHttpError(HttpStatusCode.BadRequest, "bad")
        assertTrue(e is LlmError.ProviderError && !e.retryable)
    }
}
