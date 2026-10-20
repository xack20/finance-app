package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure-Kotlin email→RawCapture mapping (shared by the Mac + Android IMAP readers). */
class EmailCaptureTest {

    @Test
    fun htmlToText_strips_tags_script_and_style() {
        val html = "<html><head><style>p{color:red}</style></head>" +
            "<body><p>You received <b>Tk 500</b></p><div>TrxID ABC</div>" +
            "<script>track()</script></body></html>"
        val text = EmailCapture.htmlToText(html)
        assertTrue(text.contains("You received Tk 500"))
        assertTrue(text.contains("TrxID ABC"))
        assertFalse(text.contains("color:red")) // <style> dropped
        assertFalse(text.contains("track()")) // <script> dropped
        assertFalse(text.contains("<")) // no tags remain
    }

    @Test
    fun htmlToText_decodes_entities_including_taka_sign() {
        assertEquals("A & B ৳100", EmailCapture.htmlToText("A &amp; B &#2547;100"))
    }

    @Test
    fun toRawCapture_strips_html_combines_subject_and_marks_email() {
        val cap = EmailCapture.toRawCapture(
            from = "alerts@bank.com",
            subject = "Transaction alert",
            body = "<p>Debit Tk 200 at SHOP</p>",
            isHtml = true,
            receivedAtMs = 123L,
        )
        assertEquals(CaptureChannel.EMAIL, cap.channel)
        assertEquals("alerts@bank.com", cap.sender)
        assertEquals(123L, cap.receivedAt)
        assertTrue(cap.body.startsWith("Transaction alert"))
        assertTrue(cap.body.contains("Debit Tk 200 at SHOP"))
        assertFalse(cap.body.contains("<p>"))
    }

    @Test
    fun toRawCapture_passes_plain_text_through_and_truncates() {
        assertEquals(
            "Sub\nPlain Tk 50 body",
            EmailCapture.toRawCapture("a@b.com", "Sub", "Plain Tk 50 body", isHtml = false, receivedAtMs = 1L).body,
        )
        val truncated = EmailCapture.toRawCapture("a@b.com", "", "x".repeat(10_000), isHtml = false, receivedAtMs = 1L, maxChars = 100)
        assertEquals(100, truncated.body.length)
    }
}
