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
}
