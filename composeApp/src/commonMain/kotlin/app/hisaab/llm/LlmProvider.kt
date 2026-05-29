package app.hisaab.llm

import app.hisaab.domain.Category

/**
 * One uniform interface over the on-device engine and each cloud vendor.
 * Implementations land in M3-4 (AndroidOnDeviceProvider + Claude/Gemini/OpenAI
 * Ktor adapters). The pipeline only ever sees this interface, so its routing
 * logic is fully unit-testable with fakes.
 */
interface LlmProvider {
    val id: ProviderId

    /** True when the engine can actually run (model downloaded / API key present). */
    suspend fun isAvailable(): Boolean

    /** Parse an SMS body into structured fields. Never throws for a "can't parse"; returns low-confidence/empty. */
    suspend fun parse(req: ParseRequest): LlmParseResult

    /** Map a merchant name to one of [categories] ids, or null when unsure. */
    suspend fun categorize(merchant: String, categories: List<Category>): String?
}
