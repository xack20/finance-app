package app.hisaab.llm

/**
 * Selects the active LlmProvider from CaptureConfig at call time, or null when
 * no engine is configured/available (template-only mode). M3-4 ships the real
 * DefaultLlmRouter; M3-3 ships NoOpLlmRouter so the pipeline compiles and runs
 * template-only until then.
 */
interface LlmRouter {
    suspend fun active(): LlmProvider?
}

/**
 * No-op router: always returns null, so CapturePipeline takes the template-only
 * path and routes unresolved financial messages to PENDING with parse_error.
 * Replaced by DefaultLlmRouter in M3-4.
 */
class NoOpLlmRouter : LlmRouter {
    override suspend fun active(): LlmProvider? = null
}
