package app.hisaab.llm

import app.hisaab.domain.Direction

/**
 * Structured output from any LlmProvider.parse(), decoded from the vendor's
 * native structured-output mode (M3-4). [isFinancial] lets the model reject a
 * non-transaction message; [confidence] is the model's 0..1 self-report.
 * All transaction fields are nullable so a partial parse degrades to review.
 */
data class LlmParseResult(
    val amount: Double?,
    val direction: Direction?,
    val merchant: String?,
    val categoryId: String?,
    val balanceAfter: Double?,
    val refNo: String?,
    val confidence: Double,
    val isFinancial: Boolean,
)
