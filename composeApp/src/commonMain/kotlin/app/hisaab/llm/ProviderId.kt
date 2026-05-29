package app.hisaab.llm

/**
 * Identifies which LLM engine produced a parse. Mirrors the cloud/on-device
 * options surfaced in CaptureConfig. Used by LlmProvider implementations
 * (M3-4) and recorded on CandidateTransaction.parsedBy.
 */
enum class ProviderId {
    ON_DEVICE,
    CLOUD_CLAUDE,
    CLOUD_GEMINI,
    CLOUD_OPENAI,
}
