package app.hisaab.llm

/**
 * Pure PII masking applied before any cloud LLM call (when redaction is enabled).
 *
 * Masks, in order:
 *  - BD phone numbers (01XXXXXXXXX / +88 01XXXXXXXXX) -> [PHONE *NNNN]
 *  - account numbers (digit runs >= 8, optionally after A/C) -> [ACCT *NNNN]
 *  - capitalised proper-name runs after a title (Mr/Mrs/Md/Mst) -> [NAME]
 *
 * Deliberately preserves amounts (with currency markers Tk/৳/BDT), TrxID/refs,
 * and merchant words. Honest limitation: amount + merchant still leave the device.
 *
 * Idempotent: re-running over already-masked text is a no-op (placeholders contain
 * no maskable digit/name runs).
 */
object Redactor {

    private const val ACCT_MIN_DIGITS = 8

    // +88 01XXXXXXXXX or 01XXXXXXXXX (BD mobile, 11 digits starting 01).
    private val phoneRegex = Regex("""(?:\+?88)?0?1[3-9]\d{8}""")

    // Any run of >= 8 digits (account numbers, card numbers); after phones are masked.
    private val acctRegex = Regex("""\d{$ACCT_MIN_DIGITS,}""")

    // Title + one or two Capitalised words (proper name run).
    private val nameRegex =
        Regex("""\b(?:Mr|Mrs|Ms|Md|Mst|Mister|Miss)\.?\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)?""")

    fun redact(text: String): String {
        var out = text

        out = phoneRegex.replace(out) { m ->
            val digits = m.value.filter { it.isDigit() }
            "[PHONE *${digits.takeLast(4)}]"
        }

        out = acctRegex.replace(out) { m ->
            "[ACCT *${m.value.takeLast(4)}]"
        }

        out = nameRegex.replace(out) { _ -> "[NAME]" }

        return out
    }

    // One-time codes: only in messages that talk about a code, and never a number written as money.
    private val codeWordRegex =
        Regex("""(?i)\b(?:otp|code|pin|password|passcode|verification)\b|ওটিপি|কোড|পিন|পাসওয়ার্ড""")
    // A run of 4-8 digits, or a code written in groups ("123-456", "456 789").
    private val digitRunRegex = Regex("""[0-9০-৯]{3,4}(?:[- ][0-9০-৯]{3,4})+|[0-9০-৯]{4,8}""")
    private val currencyBeforeRegex = Regex("""(?i)(?:tk\.?|bdt|৳|taka|টাকা)\s*$""")

    /**
     * Masks OTP/PIN/verification codes as [CODE]. Applied before every cloud call, whatever the redaction
     * setting: a code is useless to the agent and must never leave the phone.
     */
    fun maskSecrets(text: String): String {
        if (!codeWordRegex.containsMatchIn(text)) return text
        return digitRunRegex.replace(text) { m ->
            val before = text.substring(0, m.range.first)
            val after = text.substring(m.range.last + 1)
            val partOfLongerNumber = before.lastOrNull()?.let { it.isAnyDigit() || it == '.' || it == ',' } == true ||
                after.firstOrNull()?.isAnyDigit() == true ||
                (after.length >= 2 && (after[0] == '.' || after[0] == ',') && after[1].isAnyDigit())
            if (partOfLongerNumber || currencyBeforeRegex.containsMatchIn(before.takeLast(8))) m.value else "[CODE]"
        }
    }

    private fun Char.isAnyDigit() = this in '0'..'9' || this in '০'..'৯'
}
