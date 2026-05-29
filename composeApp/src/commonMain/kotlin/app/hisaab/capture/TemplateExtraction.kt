package app.hisaab.capture

import app.hisaab.domain.Direction

/**
 * Result of running a BankTemplate over an SMS body. [complete] is true only
 * when the template fully resolved a transaction (amount + direction present);
 * a partial/false result falls through to the LLM in CapturePipeline.
 */
data class TemplateExtraction(
    val amount: Double?,
    val direction: Direction?,
    val merchant: String?,
    val refNo: String?,
    val balanceAfter: Double?,
    val complete: Boolean,
)
