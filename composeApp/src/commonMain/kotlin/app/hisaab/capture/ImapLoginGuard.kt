package app.hisaab.capture

/**
 * Stops the email source from retrying a login the server already rejected: re-sending a bad app password
 * every 30 s can get the account rate-limited or locked. Changing the credentials (a new key) re-arms it.
 */
class ImapLoginGuard {
    private var rejectedKey: String? = null

    fun shouldAttempt(credentialsKey: String): Boolean = credentialsKey != rejectedKey

    fun recordAuthFailure(credentialsKey: String) {
        rejectedKey = credentialsKey
    }

    fun recordSuccess() {
        rejectedKey = null
    }
}
