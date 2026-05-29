package app.hisaab.capture

/**
 * Normalizes Bengali (Bangla) digits ০–৯ (U+09E6..U+09EF) to ASCII 0–9.
 * Run on an SMS body before any amount/balance regex so templates only ever
 * match ASCII digits. Non-digit characters pass through unchanged.
 */
object BanglaNumerals {

    private const val BANGLA_ZERO = '০' // U+09E6
    private const val BANGLA_NINE = '৯' // U+09EF

    fun normalize(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                if (ch in BANGLA_ZERO..BANGLA_NINE) {
                    '0' + (ch - BANGLA_ZERO)
                } else {
                    ch
                },
            )
        }
        return sb.toString()
    }
}
