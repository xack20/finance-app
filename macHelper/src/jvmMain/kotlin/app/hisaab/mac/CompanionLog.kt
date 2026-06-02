package app.hisaab.mac

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight append-only diagnostic log so a Finder-launched companion (no visible stdout) can be
 * debugged. Writes timestamped lines to `~/Library/Application Support/HisaabCompanion/companion.log`.
 * Records counts, timings, and error messages only — never credentials or message contents.
 */
object CompanionLog {

    private val file: File =
        File(System.getProperty("user.home"), "Library/Application Support/HisaabCompanion/companion.log")
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(message: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText("${stamp.format(Date())}  $message\n")
        }
    }
}
