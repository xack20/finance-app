package app.hisaab.mac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure parts of [ChatDbReader] — the Apple-epoch conversion and the
 * attributedBody heuristic — against synthetic blobs (no real chat.db / Mac needed). The SQL/JDBC
 * read path is integration-tested on a real Mac (P3 caveat).
 */
class ChatDbReaderTest {

    @Test
    fun apple_epoch_zero_maps_to_2001_in_unix_ms() {
        assertEquals(978_307_200_000L, ChatDbReader.appleNanosToUnixMs(0L))
    }

    @Test
    fun apple_nanos_convert_to_unix_ms() {
        // 2024-01-01T00:00:00Z = 1_704_067_200_000 ms; apple seconds = 725_760_000 → ×1e9 ns.
        assertEquals(1_704_067_200_000L, ChatDbReader.appleNanosToUnixMs(725_760_000_000_000_000L))
    }

    @Test
    fun decodes_short_attributedBody_string() {
        val text = "You have received Tk 500.00"
        assertEquals(text, ChatDbReader.decodeAttributedBody(shortBlob(text)))
    }

    @Test
    fun decodes_long_attributedBody_string() {
        val text = "x".repeat(300) // > 127 chars → 0x81 + 2-byte little-endian length
        assertEquals(text, ChatDbReader.decodeAttributedBody(longBlob(text)))
    }

    @Test
    fun returns_null_without_NSString_marker() {
        assertNull(ChatDbReader.decodeAttributedBody(byteArrayOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun returns_null_when_length_overruns_buffer() {
        // marker + '+' + length=99 but no following bytes → null (skip, don't mis-parse).
        val blob = "junkNSString".encodeToByteArray() + byteArrayOf(0x2B, 99)
        assertNull(ChatDbReader.decodeAttributedBody(blob))
    }

    private fun shortBlob(text: String): ByteArray {
        val utf8 = text.encodeToByteArray()
        require(utf8.size < 0x81)
        return "junkNSString".encodeToByteArray() + byteArrayOf(0x2B, utf8.size.toByte()) + utf8
    }

    private fun longBlob(text: String): ByteArray {
        val utf8 = text.encodeToByteArray()
        val lo = (utf8.size and 0xFF).toByte()
        val hi = ((utf8.size shr 8) and 0xFF).toByte()
        return "junkNSString".encodeToByteArray() + byteArrayOf(0x2B, 0x81.toByte(), lo, hi) + utf8
    }
}
