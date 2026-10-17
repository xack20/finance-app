package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import com.sun.mail.imap.IMAPFolder
import java.util.Date
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SearchTerm
import javax.mail.search.SubjectTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * In-app Android email capture (EP3): reads the user's mailbox over IMAP via the JavaMail Android port
 * (javax.mail / com.sun.mail:android-mail) and feeds bank emails into the existing [CapturePipeline] as
 * `RawCapture(EMAIL)` through the shared [EmailCapture] mapping — so parsing/dedup match the SMS and Mac-relay
 * paths. Blocking IMAP runs on [Dispatchers.IO]. Inert (no network) until [enabled] and [credentials] exist.
 *
 * All-time history: a server-side financial search (header-only, index-fast) picks candidate mail, then pages
 * through it oldest-first by IMAP UID ([ImapUidCursor], persisted via [loadCursor]/[saveCursor]), page after
 * page until caught up, publishing [progress]. Once caught up, polls only recent mail every [pollIntervalMs].
 * Read-only; never modifies the mailbox.
 */
class EmailCaptureSource(
    private val credentials: suspend () -> ImapConfig?,
    private val enabled: suspend () -> Boolean,
    private val loadCursor: suspend () -> Long,
    private val saveCursor: suspend (Long) -> Unit,
    private val pollIntervalMs: Long = 30_000L,
    private val pageSize: Int = 50,
    private val onError: (Throwable) -> Unit = {},
) : CaptureSource {

    private val _progress = MutableStateFlow<ImportProgress?>(null)

    /** Email history import progress for the Auto-capture screen; null once caught up. */
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    /** Set once a pass finds nothing left; later polls search only recent mail instead of the whole mailbox. */
    private var caughtUp = false
    private var readThisImport = 0

    override fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.EMAIL)

    /** Draining happens in [observeIncoming] so the cursor advances only after the page was handled. */
    override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = emptyList()

    override fun observeIncoming(): Flow<RawCapture> = flow {
        while (true) {
            while (drainPage()) Unit // history: next page straight away
            delay(pollIntervalMs)
        }
    }

    /** Reads, emits and commits one page; true while more matched mail is still waiting. */
    private suspend fun FlowCollector<RawCapture>.drainPage(): Boolean {
        if (!runCatching { enabled() }.getOrDefault(false)) return false
        val cfg = runCatching { credentials() }.getOrNull() ?: return false
        val cursor = runCatching { loadCursor() }.getOrDefault(0L)
        val page = runCatching { withContext(Dispatchers.IO) { fetchPage(cfg, cursor, recentOnly = caughtUp) } }
            .getOrElse { onError(it); _progress.value = null; return false }
        for (c in page.captures) emit(c) // suspends until the coordinator has processed each one
        runCatching { saveCursor(page.newCursor) }.onFailure { onError(it); return false }
        caughtUp = page.remaining == 0
        readThisImport = if (caughtUp) 0 else readThisImport + page.read
        _progress.value = if (caughtUp) null else ImportProgress(readThisImport, readThisImport + page.remaining)
        return !caughtUp
    }

    private class Page(val captures: List<RawCapture>, val newCursor: Long, val read: Int, val remaining: Int)

    private fun fetchPage(cfg: ImapConfig, cursor: Long, recentOnly: Boolean): Page {
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
        val store = session.getStore("imaps")
        store.connect(cfg.host, cfg.email, cfg.password)
        try {
            val folder = historyFolder(store)
            folder.open(Folder.READ_ONLY)
            try {
                val uids = folder as UIDFolder
                val validity = uids.uidValidity
                val matched = folder.search(financialSearch(recentOnly))
                folder.fetch(matched, FetchProfile().apply { add(UIDFolder.FetchProfileItem.UID) })
                val byUid = matched.associateBy { uids.getUID(it) }
                val pending = ImapUidCursor.pending(byUid.keys.toList(), cursor, validity)
                val pageUids = pending.take(pageSize)
                val messages = pageUids.mapNotNull { byUid[it] }
                if (messages.isNotEmpty()) {
                    // One round trip for the page's headers. Best effort: one unparseable envelope would
                    // otherwise fail the batch (seen on Gmail by the Mac reader).
                    runCatching {
                        folder.fetch(messages.toTypedArray(), FetchProfile().apply { add(FetchProfile.Item.ENVELOPE) })
                    }.onFailure(onError)
                }
                // Per-message defensive: a bad message is skipped, never blocks the rest of the history.
                val captures = messages.mapNotNull { m -> runCatching { toCapture(m, cfg.email) }.getOrNull() }
                val newCursor = pageUids.lastOrNull()?.let { ImapUidCursor.pack(validity, it) } ?: cursor
                return Page(captures, newCursor, read = pageUids.size, remaining = pending.size - pageUids.size)
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
    }

    /** Gmail's All Mail (IMAP `\All` special-use) so archived bank mail counts too; INBOX elsewhere. */
    private fun historyFolder(store: Store): Folder =
        runCatching {
            store.defaultFolder.list("*").firstOrNull { folder ->
                (folder as? IMAPFolder)?.attributes?.any { it.equals("\\All", ignoreCase = true) } == true
            }
        }.getOrNull() ?: store.getFolder("INBOX")

    private fun financialSearch(recentOnly: Boolean): SearchTerm {
        val keywords = OrTerm(
            (
                ImapFinancialFilter.SUBJECT_KEYWORDS.map { SubjectTerm(it) } +
                    ImapFinancialFilter.FROM_KEYWORDS.map { FromStringTerm(it) }
                ).toTypedArray(),
        )
        if (!recentOnly) return keywords
        val since = Date(System.currentTimeMillis() - RECENT_WINDOW_MS)
        return AndTerm(ReceivedDateTerm(ComparisonTerm.GE, since), keywords)
    }

    private fun toCapture(m: Message, ownAddress: String): RawCapture? {
        val ts = (m.receivedDate ?: m.sentDate)?.time ?: return null
        val from = (m.from?.firstOrNull() as? InternetAddress)?.address ?: m.from?.firstOrNull()?.toString() ?: return null
        // All Mail includes the user's own sent mail (e.g. a forwarded statement) — not a new transaction.
        if (from.equals(ownAddress, ignoreCase = true)) return null
        val (body, isHtml) = extractText(m) ?: return null
        return EmailCapture.toRawCapture(from, m.subject ?: "", body, isHtml, ts)
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
                if (!r.second) return r
                if (htmlFallback == null) htmlFallback = r
            }
            return htmlFallback
        }
        return null
    }

    private companion object {
        /** Once caught up, polls search this far back — wide enough to tolerate clock skew and late delivery. */
        const val RECENT_WINDOW_MS = 2L * 24 * 60 * 60 * 1000
    }
}
