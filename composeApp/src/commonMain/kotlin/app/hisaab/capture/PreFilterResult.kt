package app.hisaab.capture

import app.hisaab.domain.SenderMapping

/**
 * Outcome of the deterministic financial gate. NotFinancial is dropped before
 * any LLM/cloud call. KnownTemplate runs BankTemplate first. UnknownFinancial
 * goes straight to the LLM (or PENDING if no LLM).
 */
sealed interface PreFilterResult {
    /** Known promo sender (is_financial=0) or no money signal. Dropped. */
    data object NotFinancial : PreFilterResult

    /** Mapped sender with a usable BankTemplate key. */
    data class KnownTemplate(val mapping: SenderMapping, val templateKey: String) : PreFilterResult

    /** Has a money signal but no template; mapping may be null (brand-new sender). */
    data class UnknownFinancial(val mapping: SenderMapping?) : PreFilterResult
}
