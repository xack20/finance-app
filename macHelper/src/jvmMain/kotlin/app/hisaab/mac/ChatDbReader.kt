package app.hisaab.mac

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import java.sql.Connection
import java.sql.DriverManager

/**
 * Reads SMS that iOS "Text Message Forwarding" mirrors into the Mac's Messages database
 * (`~/Library/Messages/chat.db`). Requires Full Disk Access. Opens the DB read-only + immutable so
 * it never locks or mutates the live Messages store.
 *
 * Schema used (Apple's Messages DB):
 *  - message(text TEXT?, attributedBody BLOB?, date INTEGER, handle_id INTEGER, is_from_me INTEGER,
 *            service TEXT) — date is Apple-epoch NANOSECONDS since 2001-01-01.
 *  - handle(ROWID INTEGER, id TEXT) — id is the other party (phone number for SMS).
 *
 * We take incoming (`is_from_me = 0`) `service = 'SMS'` rows newer than a cursor, oldest-first, and
 * map each to a [RawCapture]. The cursor is the Apple-epoch `date` (so it round-trips exactly).
 */
class ChatDbReader(
    private val dbPath: String = System.getProperty("user.home") + "/Library/Messages/chat.db",
) {
    /** Captured rows plus the advanced cursor (max Apple-epoch `date` seen, incl. skipped rows). */
    data class Result(val captures: List<RawCapture>, val newCursor: Long)

    /** Read forwarded SMS with `date` > [sinceAppleNanos]. Throws on DB/IO error (caller handles). */
    fun readSince(sinceAppleNanos: Long): Result {
        val captures = ArrayList<RawCapture>()
        var maxDate = sinceAppleNanos
        connect().use { conn ->
            conn.prepareStatement(QUERY).use { st ->
                st.setLong(1, sinceAppleNanos)
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val date = rs.getLong("date")
                        if (date > maxDate) maxDate = date // advance even for skipped rows
                        val sender = rs.getString("sender")?.takeIf { it.isNotBlank() } ?: continue
                        val body = rs.getString("text")?.takeIf { it.isNotBlank() }
                            ?: rs.getBytes("attributedBody")?.let { decodeAttributedBody(it) }
                        if (body.isNullOrBlank()) continue
                        captures += RawCapture(
                            sender = sender,
                            body = body,
                            receivedAt = appleNanosToUnixMs(date),
                            channel = CaptureChannel.SMS,
                        )
                    }
                }
            }
        }
        return Result(captures, maxDate)
    }

    private fun connect(): Connection =
        DriverManager.getConnection("jdbc:sqlite:file:$dbPath?mode=ro&immutable=1")

    companion object {
        /** Milliseconds between 1970-01-01 (Unix) and 2001-01-01 (Apple/Cocoa) epochs. */
        private const val APPLE_EPOCH_OFFSET_MS = 978_307_200_000L

        private val QUERY = """
            SELECT m.text AS text, m.attributedBody AS attributedBody, m.date AS date, h.id AS sender
            FROM message m
            JOIN handle h ON m.handle_id = h.ROWID
            WHERE m.is_from_me = 0 AND m.service = 'SMS' AND m.date > ?
            ORDER BY m.date ASC
        """.trimIndent()

        /** Apple-epoch nanoseconds (modern macOS) → Unix epoch milliseconds. */
        fun appleNanosToUnixMs(appleNanos: Long): Long =
            appleNanos / 1_000_000L + APPLE_EPOCH_OFFSET_MS

        /**
         * Best-effort plain text from a Messages `attributedBody` blob (present when `text` is null
         * on newer macOS). The blob is an NSKeyedArchiver `streamtyped` archive; the message text is
         * an NSString. Heuristic (as used by community exporters): find "NSString", advance to the
         * '+' (0x2B) value tag, then read a length-prefixed UTF-8 run (single length byte, or 0x81 +
         * 2-byte little-endian length for longer strings).
         *
         * macOS-version-dependent and fragile — verify against the target Mac; returns null if the
         * shape isn't recognized (the row is then skipped rather than mis-parsed).
         */
        fun decodeAttributedBody(blob: ByteArray): String? {
            val marker = "NSString".toByteArray(Charsets.US_ASCII)
            val start = indexOf(blob, marker)
            if (start < 0) return null
            var i = start + marker.size
            while (i < blob.size && blob[i] != 0x2B.toByte()) i++ // seek the '+' value tag
            i++ // past '+'
            if (i >= blob.size) return null
            var len = blob[i].toInt() and 0xFF
            i++
            if (len == 0x81 && i + 1 < blob.size) { // long-string marker → 2-byte LE length
                len = (blob[i].toInt() and 0xFF) or ((blob[i + 1].toInt() and 0xFF) shl 8)
                i += 2
            }
            if (len <= 0 || i + len > blob.size) return null
            return String(blob, i, len, Charsets.UTF_8)
        }

        private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
            if (needle.isEmpty() || haystack.size < needle.size) return -1
            outer@ for (i in 0..haystack.size - needle.size) {
                for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }
    }
}
