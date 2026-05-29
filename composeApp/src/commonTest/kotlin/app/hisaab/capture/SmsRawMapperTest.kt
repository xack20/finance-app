package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmsRawMapperTest {

    @Test
    fun `maps a single-part sms to a RawCapture with SMS channel`() {
        val raw = smsPartsToRawCapture(
            sender = "bKash",
            body = "Tk 500 received from John. TrxID 9AB12C.",
            receivedAt = 1_700_000_000_000L,
        )

        assertEquals("bKash", raw!!.sender)
        assertEquals("Tk 500 received from John. TrxID 9AB12C.", raw.body)
        assertEquals(1_700_000_000_000L, raw.receivedAt)
        assertEquals(CaptureChannel.SMS, raw.channel)
    }

    @Test
    fun `concatenated multipart body is preserved verbatim as a single capture`() {
        // The receiver concatenates parts before calling the mapper; the mapper receives the
        // already-joined body. We assert it round-trips a long, joined multipart body unchanged.
        val joined =
            "Tk 12,500.00 debited from A/C ****4321 on 29-MAY for purchase at " +
                "SUPERSHOP DHAKA. Available balance Tk 3,210.55. Ref 778812. Thank you."
        val raw = smsPartsToRawCapture(
            sender = "BRAC BANK",
            body = joined,
            receivedAt = 1_700_000_123_456L,
        )

        assertEquals(joined, raw!!.body)
        assertEquals("BRAC BANK", raw.sender)
        assertEquals(1_700_000_123_456L, raw.receivedAt)
        assertEquals(CaptureChannel.SMS, raw.channel)
    }

    @Test
    fun `blank or whitespace-only body yields null`() {
        assertNull(smsPartsToRawCapture(sender = "bKash", body = "", receivedAt = 1L))
        assertNull(smsPartsToRawCapture(sender = "bKash", body = "   \n\t ", receivedAt = 1L))
    }

    @Test
    fun `blank sender falls back to the unknown-sender sentinel`() {
        val raw = smsPartsToRawCapture(sender = "", body = "Tk 10 received", receivedAt = 5L)
        assertEquals(UNKNOWN_SMS_SENDER, raw!!.sender)
    }
}
