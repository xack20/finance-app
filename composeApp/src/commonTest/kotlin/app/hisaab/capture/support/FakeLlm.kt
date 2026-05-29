package app.hisaab.capture.support

import app.hisaab.domain.Category
import app.hisaab.llm.LlmParseResult
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId

/**
 * A router that returns a fixed provider (or null for the no-LLM path).
 * Records how many times active() was called for assertions.
 */
class FakeLlmRouter(private val provider: LlmProvider?) : LlmRouter {
    var activeCallCount: Int = 0
        private set

    override suspend fun active(): LlmProvider? {
        activeCallCount++
        return provider
    }
}

/**
 * A provider that returns a canned parse result and category. Records the last
 * ParseRequest so tests can assert the pipeline passed redacted-and-correct text.
 */
class FakeLlmProvider(
    override val id: ProviderId = ProviderId.CLOUD_CLAUDE,
    private val available: Boolean = true,
    private val result: LlmParseResult,
    private val category: String? = null,
) : LlmProvider {

    var lastRequest: ParseRequest? = null
        private set

    override suspend fun isAvailable(): Boolean = available

    override suspend fun parse(req: ParseRequest): LlmParseResult {
        lastRequest = req
        return result
    }

    override suspend fun categorize(merchant: String, categories: List<Category>): String? = category
}
