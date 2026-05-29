package app.hisaab.capture

import app.hisaab.domain.Direction

/**
 * A per-institution regex template. extract() runs over an already
 * Bangla-normalized SMS body and returns structured fields. A "complete"
 * extraction requires at minimum amount + direction; otherwise CapturePipeline
 * falls through to the LLM.
 *
 * Patterns are intentionally bounded (no catastrophic backtracking): each grabs
 * a money group "N,NNN.NN" near an anchor keyword.
 */
class BankTemplate(
    val key: String,
    private val amountRegex: Regex,
    private val debitKeywords: List<Regex>,
    private val creditKeywords: List<Regex>,
    private val refRegex: Regex?,
    private val balanceRegex: Regex?,
    private val merchantRegex: Regex?,
) {

    fun extract(body: String): TemplateExtraction {
        val amount = amountRegex.find(body)?.let { parseMoney(it.groupValues[1]) }
        val direction = resolveDirection(body)
        val refNo = refRegex?.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val balance = balanceRegex?.find(body)?.let { parseMoney(it.groupValues[1]) }
        val merchant = merchantRegex?.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val complete = amount != null && amount > 0.0 && direction != null
        return TemplateExtraction(
            amount = amount,
            direction = direction,
            merchant = merchant,
            refNo = refNo,
            balanceAfter = balance,
            complete = complete,
        )
    }

    private fun resolveDirection(body: String): Direction? {
        if (creditKeywords.any { it.containsMatchIn(body) }) return Direction.CREDIT
        if (debitKeywords.any { it.containsMatchIn(body) }) return Direction.DEBIT
        return null
    }

    private fun parseMoney(raw: String): Double? =
        raw.replace(",", "").trim().toDoubleOrNull()
}

/**
 * In-app seeded templates for v1: bKash, Nagad, Rocket + City Bank, BRAC Bank,
 * DBBL. CDN-delivered patterns are deferred. Keyed by the sender_registry
 * template_key the pre-filter resolves.
 */
object BankTemplates {

    // Reusable money group: "1,500.00" / "55,000" / "199"
    private const val MONEY = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"

    // ---- bKash ----
    private val BKASH = BankTemplate(
        key = "bkash",
        // First Tk-amount in the body is the transaction amount (Fee/Balance come later with their own anchors).
        amountRegex = Regex("(?:received|Payment|Cash Out|Send Money)[^0-9]*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("received", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(
            Regex("Payment", RegexOption.IGNORE_CASE),
            Regex("Cash Out", RegexOption.IGNORE_CASE),
            Regex("Send Money", RegexOption.IGNORE_CASE),
        ),
        refRegex = Regex("TrxID\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Balance\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = Regex("Payment\\s*Tk\\s*[0-9.,]+\\s*to\\s*([A-Za-z][A-Za-z &]+?)\\.", RegexOption.IGNORE_CASE),
    )

    // ---- Nagad ----
    private val NAGAD = BankTemplate(
        key = "nagad",
        amountRegex = Regex("Amount:\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("Money Received", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(
            Regex("Payment Successful", RegexOption.IGNORE_CASE),
            Regex("Cash Out", RegexOption.IGNORE_CASE),
        ),
        refRegex = Regex("TxnID:\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Balance:\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = Regex("To:\\s*([A-Za-z][A-Za-z &]+?)\\s+Balance", RegexOption.IGNORE_CASE),
    )

    // ---- Rocket ----
    private val ROCKET = BankTemplate(
        key = "rocket",
        amountRegex = Regex("Tk\\s*$MONEY\\s*(?:credited|debited)", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("credited", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(Regex("debited", RegexOption.IGNORE_CASE)),
        refRegex = Regex("TxnId\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Balance\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = null,
    )

    // ---- City Bank ----
    private val CITYBANK = BankTemplate(
        key = "citybank",
        amountRegex = Regex("(?:debited|credited)\\s*BDT\\s*$MONEY", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("is credited", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(Regex("is debited", RegexOption.IGNORE_CASE)),
        refRegex = Regex("Ref\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Avail Bal\\s*BDT\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = Regex("at\\s*([A-Z][A-Z &]+?)\\.\\s*Avail", RegexOption.IGNORE_CASE),
    )

    // ---- BRAC Bank ----
    private val BRACBANK = BankTemplate(
        key = "bracbank",
        amountRegex = Regex("BDT\\s*$MONEY\\s*has been", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("credited to", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(Regex("debited from", RegexOption.IGNORE_CASE)),
        refRegex = Regex("TXN\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Available Balance\\s*BDT\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = null,
    )

    // ---- DBBL ----
    private val DBBL = BankTemplate(
        key = "dbbl",
        amountRegex = Regex("(?:debited|credited) by\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("credited by", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(Regex("debited by", RegexOption.IGNORE_CASE)),
        refRegex = Regex("Ref\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Balance\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = null,
    )

    private val byKey: Map<String, BankTemplate> = listOf(
        BKASH, NAGAD, ROCKET, CITYBANK, BRACBANK, DBBL,
    ).associateBy { it.key }

    val keys: List<String> = byKey.keys.toList()

    fun forKey(key: String): BankTemplate? = byKey[key]
}
