package app.hisaab.llm

/**
 * Typed cloud/provider failure. Carried by [LlmException] so callers
 * (CapturePipeline via LlmRouter) can map to a candidate parse_error.
 */
sealed class LlmError(val message: String, val retryable: Boolean) {
    data object InvalidKey : LlmError("API key is invalid", retryable = false)
    data object RateLimited : LlmError("Rate limited; will retry", retryable = true)
    data object Unavailable : LlmError("Provider unavailable", retryable = false)
    data class Network(val detail: String) : LlmError("Network error: $detail", retryable = true)
    data class ProviderError(val status: Int, val detail: String) :
        LlmError("Provider error ($status): $detail", retryable = status >= 500)
    data class Decode(val detail: String) : LlmError("Failed to decode response: $detail", retryable = false)
}

/** Thrown by providers on failure; [error] holds the typed cause. */
class LlmException(val error: LlmError) : Exception(error.message)
