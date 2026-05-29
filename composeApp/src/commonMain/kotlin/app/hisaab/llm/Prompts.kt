package app.hisaab.llm

import app.hisaab.domain.Category

/**
 * The single extraction system prompt (full + on-device short variant) and the
 * merchant categorize prompt. All variants constrain category output to exactly
 * the provided category ids (the 12 seeded ids) and require strict JSON.
 */
object Prompts {

    private fun idList(categories: List<Category>): String =
        categories.joinToString(", ") { "\"${it.id}\" (${it.name})" }

    private fun idArray(categories: List<Category>): String =
        categories.joinToString(", ") { "\"${it.id}\"" }

    /** Full extraction system prompt with BD few-shot. */
    fun extractionSystem(categories: List<Category>): String = buildString {
        appendLine("You extract structured fields from a single Bangladeshi bank/MFS SMS.")
        appendLine("Reply with ONE JSON object and nothing else. No markdown, no prose.")
        appendLine()
        appendLine("Schema (all keys required; use null when unknown):")
        appendLine("""{"amount": number|null, "direction": "DEBIT"|"CREDIT"|null,""")
        appendLine(""" "merchant": string|null, "categoryId": string|null,""")
        appendLine(""" "balanceAfter": number|null, "refNo": string|null,""")
        appendLine(""" "confidence": number, "isFinancial": boolean}""")
        appendLine()
        appendLine("Rules:")
        appendLine("- If the message is not a financial transaction (promo/OTP/balance-only), set isFinancial=false and all other fields null except confidence.")
        appendLine("- direction: money leaving the user = DEBIT, money arriving = CREDIT.")
        appendLine("- categoryId MUST be exactly one of: ${idList(categories)}. Use null if unsure.")
        appendLine("- confidence is your 0..1 self-estimate that the extraction is correct.")
        appendLine("- Amounts may use Tk, ৳ or BDT and Bangla digits; output a plain number.")
        appendLine()
        appendLine("Examples:")
        appendLine("SMS: \"You have received Tk 1,500.00 from 017XXXXXXXX. Fee Tk 0.00. Balance Tk 3,210.50. TrxID 9AB12CD34\"")
        appendLine("""JSON: {"amount":1500.0,"direction":"CREDIT","merchant":null,"categoryId":null,"balanceAfter":3210.5,"refNo":"9AB12CD34","confidence":0.95,"isFinancial":true}""")
        appendLine("SMS: \"Payment Tk 320 to SHWAPNO successful via bKash. Balance Tk 1,008.\"")
        appendLine("""JSON: {"amount":320.0,"direction":"DEBIT","merchant":"Shwapno","categoryId":"food","balanceAfter":1008.0,"refNo":null,"confidence":0.9,"isFinancial":true}""")
        appendLine("SMS: \"Get 20% cashback this Eid! Recharge now with bKash.\"")
        appendLine("""JSON: {"amount":null,"direction":null,"merchant":null,"categoryId":null,"balanceAfter":null,"refNo":null,"confidence":0.99,"isFinancial":false}""")
    }

    /** Shorter variant for the on-device model (token-constrained). */
    fun extractionSystemShort(categories: List<Category>): String = buildString {
        appendLine("Extract fields from a Bangladeshi bank/MFS SMS. Reply with ONE JSON object only.")
        appendLine("""Keys: amount(number|null), direction("DEBIT"|"CREDIT"|null), merchant(string|null),""")
        appendLine(""" categoryId(one of [${idArray(categories)}] or null), balanceAfter(number|null),""")
        appendLine(""" refNo(string|null), confidence(0..1 number), isFinancial(boolean).""")
        appendLine("Promo/OTP/non-money -> isFinancial=false, others null. DEBIT=money out, CREDIT=money in.")
    }

    /** Merchant -> categoryId prompt for the template-only categorize fast path. */
    fun categorize(merchant: String, categories: List<Category>): String = buildString {
        appendLine("Pick the single best category id for this merchant.")
        appendLine("Merchant: \"$merchant\"")
        appendLine("Allowed ids: [${idArray(categories)}].")
        appendLine("Reply with ONE JSON object: {\"categoryId\": string|null}. Use null if none fit.")
    }
}
