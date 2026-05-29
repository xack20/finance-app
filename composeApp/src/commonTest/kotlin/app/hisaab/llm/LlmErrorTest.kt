package app.hisaab.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmErrorTest {

    @Test
    fun `invalid key is not retryable`() {
        assertFalse(LlmError.InvalidKey.retryable)
    }

    @Test
    fun `rate limited is retryable`() {
        assertTrue(LlmError.RateLimited.retryable)
    }

    @Test
    fun `network error is retryable`() {
        assertTrue(LlmError.Network("boom").retryable)
    }

    @Test
    fun `message describes the failure`() {
        assertEquals("API key is invalid", LlmError.InvalidKey.message)
        assertTrue(LlmError.ProviderError(500, "down").message.contains("500"))
    }
}
