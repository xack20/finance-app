package app.hisaab.mac

import app.hisaab.capture.EmailCapture
import app.hisaab.capture.ImapConfig
import app.hisaab.domain.RawCapture
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.search.AndTerm
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.FromStringTerm
import jakarta.mail.search.OrTerm
import jakarta.mail.search.ReceivedDateTerm
import jakarta.mail.search.SearchTerm
import jakarta.mail.search.SubjectTerm
import java.util.Date
import java.util.Properties

/**
 * Reads transaction emails from the user's INBOX over IMAPS (Angus/Jakarta Mail, JVM) and maps each
 * to a [RawCapture] (channel=EMAIL) via the shared [EmailCapture]. Mirrors [ChatDbReader]: fetch
 * messages received after a cursor (Unix ms), oldest-first, advancing the cursor to the last one read.
 * Read-only; never modifies the mailbox.
 *
 * Backfill is PAGED: each call returns at most [maxPerPass] messages (oldest first) and advances the
 * cursor to the last one taken, so successive poll ticks walk through a large 12-month window a page
 * at a time instead of loading the whole inbox into memory / one giant relay upload.
 */
class ImapReader(
    private val config: ImapConfig,
    private val maxPerPass: Int = MAX_PER_PASS,
) {

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
        var newCursor = sinceMs
        val t0 = System.currentTimeMillis()
        val store = session.getStore("imaps")
        store.connect(config.host, config.email, config.password)
        CompanionLog.log("imap connected in ${System.currentTimeMillis() - t0}ms; reading since=$sinceMs")
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                // Server-side filter by received date (day granularity). IMAP SEARCH returns ascending
                // sequence order = arrival order = receivedDate order for the INBOX, so the first
                // maxPerPass are the oldest unsynced — take just that page (bounds fetch/upload/memory).
                val tSearch = System.currentTimeMillis()
                // Server-side narrow to LIKELY-financial mail (date + subject/from keywords). A busy
                // INBOX can hold tens of thousands of messages over a year; fetching every body to let
                // the phone discard 99% would take hours. Gmail evaluates this header search on its
                // index, so we only fetch the few hundred that matter. The phone still does the final
                // precise filtering on what's forwarded.
                val matched = inbox.search(financialSearch(sinceMs))
                val page = matched.take(maxPerPass)
                CompanionLog.log("imap financial-search matched=${matched.size} page=${page.size} in ${System.currentTimeMillis() - tSearch}ms")
                // Best-effort envelope prefetch for the page (one round trip). Wrapped: Gmail sometimes
                // has a message jakarta.mail can't parse, and a batch fetch would otherwise throw
                // "Failed to load IMAP envelope" and abort the whole pass.
                if (page.isNotEmpty()) {
                    runCatching { inbox.fetch(page.toTypedArray(), FetchProfile().apply { add(FetchProfile.Item.ENVELOPE) }) }
                        .onFailure { CompanionLog.log("imap envelope prefetch failed: ${it.message}") }
                }
                var skipped = 0
                for (m in page) {
                    // Every per-message access is defensive: skip an unparseable message rather than
                    // failing the batch (one bad envelope shouldn't block months of good mail).
                    val ts = runCatching { (m.receivedDate ?: m.sentDate)?.time }.getOrNull() ?: run { skipped++; continue }
                    if (ts <= sinceMs) continue // day-granular search can include same-day older mail
                    if (ts > newCursor) newCursor = ts
                    val from = runCatching {
                        (m.from?.firstOrNull() as? InternetAddress)?.address ?: m.from?.firstOrNull()?.toString()
                    }.getOrNull() ?: run { skipped++; continue }
                    val (body, isHtml) = runCatching { extractText(m) }.getOrNull() ?: run { skipped++; continue }
                    val subject = runCatching { m.subject }.getOrNull() ?: ""
                    captures += EmailCapture.toRawCapture(from, subject, body, isHtml, ts)
                }
                CompanionLog.log("imap read done: captured=${captures.size} skipped=$skipped newCursor=$newCursor total=${System.currentTimeMillis() - t0}ms")
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
        return Result(captures, newCursor)
    }

    /**
     * `received >= since AND (subject matches any financial keyword OR from matches a money sender)`.
     * Header-only terms so Gmail/most servers answer from their index — fast even on a huge mailbox.
     */
    private fun financialSearch(sinceMs: Long): SearchTerm {
        val keywords = OrTerm(
            (FINANCIAL_SUBJECT.map { SubjectTerm(it) } + FINANCIAL_FROM.map { FromStringTerm(it) }).toTypedArray(),
        )
        return if (sinceMs > 0) AndTerm(ReceivedDateTerm(ComparisonTerm.GE, Date(sinceMs)), keywords) else keywords
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

    private companion object {
        /** Max emails processed per poll tick — bounds memory, IMAP body fetches, and upload size.
         *  Small so each pass finishes quickly and the synced counter visibly climbs during backfill. */
        const val MAX_PER_PASS = 50

        // Coarse server-side financial filter. Broad for recall (the phone does the precise filtering);
        // header-only so the search stays index-fast. Covers BD MFS/banks + generic money terms.
        val FINANCIAL_SUBJECT = listOf(
            "transaction", "debited", "credited", "debit", "credit", "payment", "balance",
            "statement", "BDT", "Taka", "spent", "withdraw", "deposit", "transfer", "purchase",
            "bKash", "Nagad", "Rocket", "bill", "recharge", "OTP", "card", "fund", "EMI", "due",
            "invoice", "receipt", "account",
        )
        val FINANCIAL_FROM = listOf("bank", "bkash", "nagad", "rocket", "card")
    }
}
