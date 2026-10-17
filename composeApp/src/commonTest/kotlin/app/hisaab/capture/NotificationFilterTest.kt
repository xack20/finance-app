package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationFilterTest {

    private val own = "app.hisaab"

    @Test
    fun `Hisaab's own notifications are never captured`() {
        assertFalse(shouldCaptureNotification(own, own, ongoing = false, text = "Reading messages: 5 of 9 · Tk 500"))
    }

    @Test
    fun `ongoing notifications are skipped`() {
        assertFalse(shouldCaptureNotification("com.bkash", own, ongoing = true, text = "Sending Tk 500…"))
    }

    @Test
    fun `chats without money never leave the phone`() {
        assertFalse(shouldCaptureNotification("com.whatsapp", own, ongoing = false, text = "Are you coming tonight?"))
        assertFalse(shouldCaptureNotification("com.whatsapp", own, ongoing = false, text = "Your code is 4411"))
    }

    @Test
    fun `payment notifications are captured`() {
        assertTrue(shouldCaptureNotification("com.bKash.customerapp", own, false, "You have received Tk 500.00 from 01712345678"))
        assertTrue(shouldCaptureNotification("com.bank", own, false, "৳1,200 paid to Daraz"))
        assertTrue(shouldCaptureNotification("com.bank", own, false, "BDT 3,000 debited from A/C"))
        assertTrue(shouldCaptureNotification("com.nagad", own, false, "৫০০ টাকা পাঠানো হয়েছে"))
        assertTrue(shouldCaptureNotification("com.nagad", own, false, "Cash out done. TrxID 9JK2X7"))
    }
}
