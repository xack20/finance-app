package app.hisaab.mac

/**
 * Stores the IMAP app-password in the macOS login Keychain via the `security` CLI, so it isn't
 * written to disk in plaintext and survives relaunches. The email address is the keychain account.
 */
object MacKeychain {

    private const val SERVICE = "HisaabCompanion-IMAP"

    fun savePassword(account: String, password: String) {
        // -U updates if it already exists.
        runSecurity("add-generic-password", "-s", SERVICE, "-a", account, "-w", password, "-U")
    }

    fun loadPassword(account: String): String? = runCatching {
        val proc = ProcessBuilder("security", "find-generic-password", "-s", SERVICE, "-a", account, "-w")
            .redirectErrorStream(false)
            .start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() == 0 && out.isNotEmpty()) out else null
    }.getOrNull()

    private fun runSecurity(vararg args: String) {
        runCatching { ProcessBuilder(listOf("security") + args).start().waitFor() }
    }
}
