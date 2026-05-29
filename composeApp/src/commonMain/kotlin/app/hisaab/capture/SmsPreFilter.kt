package app.hisaab.capture

import app.hisaab.data.SenderRepository
import app.hisaab.domain.RawCapture
import app.hisaab.domain.SenderMapping

/**
 * Deterministic, offline "is this a financial transaction?" gate. Drops
 * non-financial messages BEFORE any LLM/cloud call. classify() is pure given
 * the pre-resolved [mapping] (the pipeline does the repo lookup); the
 * SenderRepository is held for future sender-promotion lookups and to match the
 * shared constructor contract.
 *
 * A money signal = a currency token (Tk / ৳ / BDT), a TrxID/TxnID/TXN ref, or a
 * debit/credit keyword. A sender explicitly flagged is_financial=0 is always
 * NotFinancial regardless of body.
 */
class SmsPreFilter(
    @Suppress("unused") private val senderRegistry: SenderRepository,
) {

    fun classify(raw: RawCapture, mapping: SenderMapping?): PreFilterResult {
        // Explicit promo/non-financial sender → drop.
        if (mapping != null && !mapping.isFinancial) return PreFilterResult.NotFinancial

        val body = BanglaNumerals.normalize(raw.body)
        if (!hasMoneySignal(body)) return PreFilterResult.NotFinancial

        val templateKey = mapping?.templateKey
        if (mapping != null && !templateKey.isNullOrBlank() && BankTemplates.forKey(templateKey) != null) {
            return PreFilterResult.KnownTemplate(mapping, templateKey)
        }
        return PreFilterResult.UnknownFinancial(mapping)
    }

    private fun hasMoneySignal(body: String): Boolean {
        if (CURRENCY.containsMatchIn(body) && AMOUNT.containsMatchIn(body)) return true
        if (REF.containsMatchIn(body)) return true
        if (DIRECTION.containsMatchIn(body)) return true
        return false
    }

    private companion object {
        val CURRENCY = Regex("\\b(?:Tk|BDT)\\b|৳", RegexOption.IGNORE_CASE) // ৳ = U+09F3
        val AMOUNT = Regex("[0-9][0-9,]*(?:\\.[0-9]{1,2})?")
        val REF = Regex("\\b(?:TrxID|TxnID|TxnId|TXN)\\b", RegexOption.IGNORE_CASE)
        val DIRECTION = Regex("\\b(?:credited|debited|received|Cash Out|Send Money|Payment)\\b", RegexOption.IGNORE_CASE)
    }
}
