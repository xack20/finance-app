package app.hisaab.capture

/** IMAP connection settings. [password] is an app-specific password (consumer Gmail/Outlook.com). */
data class ImapConfig(val email: String, val password: String, val host: String, val port: Int = 993)

/**
 * Coarse server-side "likely financial" IMAP search terms, shared by the Mac helper and the Android reader.
 * Header-only so Gmail answers from its index even on a huge mailbox; broad for recall — the phone's
 * pipeline does the precise filtering. Covers BD MFS/banks plus generic money words.
 */
object ImapFinancialFilter {
    val SUBJECT_KEYWORDS = listOf(
        "transaction", "debited", "credited", "debit", "credit", "payment", "balance",
        "statement", "BDT", "Taka", "spent", "withdraw", "deposit", "transfer", "purchase",
        "bKash", "Nagad", "Rocket", "bill", "recharge", "OTP", "card", "fund", "EMI", "due",
        "invoice", "receipt", "account",
    )
    val FROM_KEYWORDS = listOf("bank", "bkash", "nagad", "rocket", "card")
}

/**
 * Known IMAPS hosts for consumer providers, derived from the email domain. Shared by the Mac helper
 * (Angus Mail) and the in-app Android reader (android-mail). Returns null for Workspace/M365/custom
 * domains, which require OAuth2 (XOAUTH2) rather than an app password.
 */
object ImapHosts {
    fun forEmail(email: String): Pair<String, Int>? = when (email.substringAfter('@', "").lowercase()) {
        "gmail.com", "googlemail.com" -> "imap.gmail.com" to 993
        "outlook.com", "hotmail.com", "live.com", "msn.com" -> "outlook.office365.com" to 993
        "yahoo.com" -> "imap.mail.yahoo.com" to 993
        "icloud.com", "me.com", "mac.com" -> "imap.mail.me.com" to 993
        else -> null
    }
}
