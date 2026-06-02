package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import java.util.Date
import java.util.Properties
import javax.mail.Folder
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * In-app Android email capture (EP3): polls the user's IMAP INBOX via the JavaMail Android port
 * (javax.mail / com.sun.mail:android-mail) and feeds bank emails into the existing [CapturePipeline]
 * as `RawCapture(EMAIL)` through the shared [EmailCapture] mapping — so parsing/dedup match the SMS
 * and Mac-relay paths. Blocking IMAP runs on [Dispatchers.IO]. Stays inert (no network) until
 * [enabled] and [credentials] are both present; the cursor is persisted via [loadCursor]/[saveCursor].
 */
class EmailCaptureSource(
    private val credentials: suspend () -> ImapConfig?,
    private val enabled: suspend () -> Boolean,
    private val loadCursor: suspend () -> Long,
    private val saveCursor: suspend (Long) -> Unit,
    private val pollIntervalMs: Long = 30_000L,
    private val onError: (Throwable) -> Unit = {},
) : CaptureSource {

    override fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.EMAIL)

    /** Draining happens in [observeIncoming] so the cursor advances only after a successful fetch. */
    override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = emptyList()

    override fun observeIncoming(): Flow<RawCapture> = flow {
        while (true) {
            drain()
            delay(pollIntervalMs)
        }
    }

    private suspend fun FlowCollector<RawCapture>.drain() {
        if (!runCatching { enabled() }.getOrDefault(false)) return
        val cfg = runCatching { credentials() }.getOrNull() ?: return
        val cursor = runCatching { loadCursor() }.getOrDefault(0L)
        val (captures, newCursor) = runCatching { withContext(Dispatchers.IO) { fetchSince(cfg, cursor) } }
            .getOrElse { onError(it); return }
        for (c in captures) emit(c)
        runCatching { saveCursor(newCursor) }.onFailure { onError(it) }
    }

    private fun fetchSince(cfg: ImapConfig, sinceMs: Long): Pair<List<RawCapture>, Long> {
        val session = Session.getInstance(
            Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", cfg.host)
                put("mail.imaps.port", cfg.port.toString())
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.connectiontimeout", "15000")
                put("mail.imaps.timeout", "20000")
            },
        )
        val captures = ArrayList<RawCapture>()
        var maxMs = sinceMs
        val store = session.getStore("imaps")
        store.connect(cfg.host, cfg.email, cfg.password)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val messages =
                    if (sinceMs > 0) inbox.search(ReceivedDateTerm(ComparisonTerm.GE, Date(sinceMs))) else inbox.messages
                for (m in messages.sortedBy { (it.receivedDate ?: it.sentDate ?: Date(0)).time }) {
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
        return captures to maxMs
    }

    private fun extractText(part: Part): Pair<String, Boolean>? {
        if (part.isMimeType("text/plain")) return (part.content as? String)?.let { it to false }
        if (part.isMimeType("text/html")) return (part.content as? String)?.let { it to true }
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as? Multipart ?: return null
            var htmlFallback: Pair<String, Boolean>? = null
            for (i in 0 until mp.count) {
                val r = extractText(mp.getBodyPart(i)) ?: continue
                if (!r.second) return r
                if (htmlFallback == null) htmlFallback = r
            }
            return htmlFallback
        }
        return null
    }
}
