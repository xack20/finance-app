package app.hisaab.mac

/** Deep-links into the macOS panes needed for one-time setup. */
object SystemSettings {

    /** System Settings → Privacy & Security → Full Disk Access (grant the helper to read chat.db). */
    fun openFullDiskAccess() {
        open("x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles")
    }

    /** Opens Messages so the user can confirm it's signed into the same iMessage account. */
    fun openMessages() {
        open("/System/Applications/Messages.app")
    }

    private fun open(target: String) {
        runCatching { ProcessBuilder("open", target).start() }
    }
}
