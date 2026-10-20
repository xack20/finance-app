package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture

/**
 * Shared (JVM + Android) helpers that turn a fetched email into a [RawCapture] the existing pipeline
 * can parse. Both the Mac helper (Angus Mail) and the Android in-app reader (android-mail) produce
 * captures via [toRawCapture], so parsing + dedup behave identically regardless of where IMAP runs.
 *
 * Pure Kotlin (no platform deps) so it lives in :sharedCore. The fetcher extracts a text body
 * (preferring text/plain; falling back to HTML stripped here), so the channel-agnostic money-signal
 * pre-filter and the LLM parser run unchanged on the result.
 */
object EmailCapture {

    /** Cap the body so long HTML receipts / reply-all threads don't blow LLM token budgets. */
    const val DEFAULT_MAX_CHARS: Int = 4000

    /**
     * Build a [RawCapture] from a fetched email. `sender` = the From address; `body` = subject +
     * the plain-text body (HTML stripped when [isHtml]), trimmed and truncated to [maxChars].
     */
    fun toRawCapture(
        from: String,
        subject: String,
        body: String,
        isHtml: Boolean,
        receivedAtMs: Long,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): RawCapture {
        val text = if (isHtml) htmlToText(body) else body
        val combined = buildString {
            if (subject.isNotBlank()) {
                append(subject.trim())
                append('\n')
            }
            append(text.trim())
        }.trim()
        return RawCapture(
            sender = from.trim(),
            body = combined.take(maxChars),
            receivedAt = receivedAtMs,
            channel = CaptureChannel.EMAIL,
        )
    }

    /** Strip HTML to readable text: drop script/style, turn block tags into newlines, decode common entities, collapse whitespace. */
    fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
        s = s.replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), "\n")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = decodeEntities(s)
        s = s.replace(Regex("[ \\t\\x0B\\u000C\\r]+"), " ")
        s = s.replace(Regex("\\n{2,}"), "\n")
        return s.trim()
    }

    private fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        // Numeric decimal entities (BMP only), e.g. &#2547; → ৳ (taka sign). Non-BMP left as-is.
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 1..0xFFFF }?.toChar()?.toString() ?: m.value
        }
}
