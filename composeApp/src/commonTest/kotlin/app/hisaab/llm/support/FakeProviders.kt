package app.hisaab.llm.support

import app.hisaab.domain.Category
import app.hisaab.llm.LlmParseResult
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId

/** Fake provider with controllable availability + canned result. */
class FakeProvider(
    override val id: ProviderId,
    private val available: Boolean,
    private val result: LlmParseResult = LlmParseResult(
        amount = 1.0, direction = null, merchant = null, categoryId = null,
        balanceAfter = null, refNo = null, confidence = 0.5, isFinancial = true,
    ),
) : LlmProvider {
    override suspend fun isAvailable(): Boolean = available
    override suspend fun parse(req: ParseRequest): LlmParseResult = result
    override suspend fun categorize(merchant: String, categories: List<Category>): String? = null
}
