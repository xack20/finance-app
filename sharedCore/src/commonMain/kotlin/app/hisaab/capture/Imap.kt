package app.hisaab.capture

/** IMAP connection settings. [password] is an app-specific password (consumer Gmail/Outlook.com). */
data class ImapConfig(val email: String, val password: String, val host: String, val port: Int = 993)

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
