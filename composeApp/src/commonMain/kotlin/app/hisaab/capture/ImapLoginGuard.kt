package app.hisaab.capture

/**
 * Stops the email source from retrying a login the server already rejected: re-sending a bad app password
 * every 30 s can get the account rate-limited or locked. New credentials re-arm it at once; the same ones are
 * retried after [retryAfterMs] (the user may have fixed the account side, e.g. enabled IMAP).
 */
class ImapLoginGuard(
    private val now: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
    private val retryAfterMs: Long = 15L * 60 * 1000,
) {
    private var rejectedKey: String? = null
    private var rejectedAt = 0L

    fun shouldAttempt(credentialsKey: String): Boolean =
        credentialsKey != rejectedKey || now() - rejectedAt >= retryAfterMs

    fun recordAuthFailure(credentialsKey: String) {
        rejectedKey = credentialsKey
        rejectedAt = now()
    }

    fun recordSuccess() {
        rejectedKey = null
    }
}
