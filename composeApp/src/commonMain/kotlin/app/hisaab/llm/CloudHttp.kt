package app.hisaab.llm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json

/** Cloud LLM request timeout (spec §8). */
const val LLM_TIMEOUT_MS: Long = 15_000

/**
 * Wraps an existing engine-backed [HttpClient] config; cloud providers receive
 * a shared client. This factory configures JSON negotiation + a 15s timeout on top
 * of whatever engine the caller's client uses. In production we reuse AppContainer's
 * client; tests pass a MockEngine-backed client and call configure() equivalently.
 */
fun HttpClient.llmConfigured(): HttpClient = config {
    install(ContentNegotiation) { json(LlmJson.json) }
    install(HttpTimeout) {
        requestTimeoutMillis = LLM_TIMEOUT_MS
        connectTimeoutMillis = LLM_TIMEOUT_MS
        socketTimeoutMillis = LLM_TIMEOUT_MS
    }
}

/** Maps a non-2xx HTTP status to a typed [LlmError]. */
fun mapHttpError(status: HttpStatusCode, body: String): LlmError = when (status.value) {
    401, 403 -> LlmError.InvalidKey
    429 -> LlmError.RateLimited
    else -> LlmError.ProviderError(status.value, body.take(200))
}
