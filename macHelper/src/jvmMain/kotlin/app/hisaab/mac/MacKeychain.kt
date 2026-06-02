package app.hisaab.mac

/**
 * Stores secrets in the macOS login Keychain via the `security` CLI, so nothing is written to disk
 * in plaintext and they survive relaunches. Two namespaces: IMAP app-passwords (keyed by the email
 * address) and companion state (the Supabase session + the relay key), so the user doesn't re-verify
 * their phone or re-enter the recovery phrase on every launch.
 */
object MacKeychain {

    private const val IMAP_SERVICE = "HisaabCompanion-IMAP"
    private const val STATE_SERVICE = "HisaabCompanion-State"

    // ── IMAP app-password (account = email address) ──────────────────────────────────────────────
    fun savePassword(account: String, password: String) = save(IMAP_SERVICE, account, password)
    fun loadPassword(account: String): String? = load(IMAP_SERVICE, account)

    // ── Companion state (session JSON, relay key) — account is a fixed item name ──────────────────
    fun saveItem(account: String, value: String) = save(STATE_SERVICE, account, value)
    fun loadItem(account: String): String? = load(STATE_SERVICE, account)
    fun deleteItem(account: String) = runSecurity("delete-generic-password", "-s", STATE_SERVICE, "-a", account)

    private fun save(service: String, account: String, value: String) {
        // -U updates if it already exists.
        runSecurity("add-generic-password", "-s", service, "-a", account, "-w", value, "-U")
    }

    private fun load(service: String, account: String): String? = runCatching {
        val proc = ProcessBuilder("security", "find-generic-password", "-s", service, "-a", account, "-w")
            .redirectErrorStream(false)
            .start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() == 0 && out.isNotEmpty()) out else null
    }.getOrNull()

    private fun runSecurity(vararg args: String) {
        runCatching { ProcessBuilder(listOf("security") + args).start().waitFor() }
    }
}
