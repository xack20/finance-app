package app.hisaab.mac

import app.hisaab.capture.EmailCapture
import app.hisaab.capture.ImapConfig
import app.hisaab.capture.ImapHosts
import app.hisaab.domain.RawCapture
import jakarta.mail.Folder
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.ReceivedDateTerm
import java.util.Date
import java.util.Properties

/**
 * Reads transaction emails from the user's INBOX over IMAPS (Angus/Jakarta Mail, JVM) and maps each
 * to a [RawCapture] (channel=EMAIL) via the shared [EmailCapture]. Mirrors [ChatDbReader]: fetch
 * messages received after a cursor (Unix ms), oldest-first, advancing the cursor to the max seen.
 * Read-only; never modifies the mailbox.
 */
class ImapReader(private val config: ImapConfig) {

    data class Result(val captures: List<RawCapture>, val newCursor: Long)

    fun readSince(sinceMs: Long): Result {
        val session = Session.getInstance(
            Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", config.host)
                put("mail.imaps.port", config.port.toString())
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.connectiontimeout", "15000")
                put("mail.imaps.timeout", "20000")
            },
        )
        val captures = ArrayList<RawCapture>()
        var maxMs = sinceMs
        val store = session.getStore("imaps")
        store.connect(config.host, config.email, config.password)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                // Server-side filter by received date (day granularity), then refine by exact ms.
                val messages =
                    if (sinceMs > 0) inbox.search(ReceivedDateTerm(ComparisonTerm.GE, Date(sinceMs))) else inbox.messages
                val ordered = messages.sortedBy { (it.receivedDate ?: it.sentDate ?: Date(0)).time }
                for (m in ordered) {
                    val ts = (m.receivedDate ?: m.sentDate ?: continue).time
                    if (ts <= sinceMs) continue
                    if (ts > maxMs) maxMs = ts
                    val from = (m.from?.firstOrNull() as? InternetAddress)?.address
                        ?: m.from?.firstOrNull()?.toString() ?: continue
                    val (body, isHtml) = extractText(m) ?: continue
                    captures += EmailCapture.toRawCapture(from, m.subject ?: "", body, isHtml, ts)
                }
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
        return Result(captures, maxMs)
    }

    /** Prefer a text/plain part; fall back to text/html (stripped later). Returns (body, isHtml). */
    private fun extractText(part: Part): Pair<String, Boolean>? {
        if (part.isMimeType("text/plain")) return (part.content as? String)?.let { it to false }
        if (part.isMimeType("text/html")) return (part.content as? String)?.let { it to true }
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as? Multipart ?: return null
            var htmlFallback: Pair<String, Boolean>? = null
            for (i in 0 until mp.count) {
                val r = extractText(mp.getBodyPart(i)) ?: continue
                if (!r.second) return r // text/plain wins
                if (htmlFallback == null) htmlFallback = r
            }
            return htmlFallback
        }
        return null
    }
}
