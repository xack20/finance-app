package app.hisaab.capture

import app.hisaab.capture.ChannelStatus.Error
import app.hisaab.capture.ChannelStatus.Importing
import app.hisaab.capture.ChannelStatus.Off
import app.hisaab.capture.ChannelStatus.UpToDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusTest {

    private val t = 1_700_000_000_000L // 2023-11-14 22:13:20 UTC
    private fun tracker() = ChannelStatusTracker(now = { t })

    @Test
    fun `succeeded reports up to date at the check time`() {
        val tracker = tracker().apply { succeeded() }
        assertEquals(UpToDate(t), tracker.status.value)
    }

    @Test
    fun `a persistent failure shows at once`() {
        val tracker = tracker().apply { failed("Email login rejected", persistent = true) }
        assertEquals(Error("Email login rejected"), tracker.status.value)
    }

    @Test
    fun `transient failures show only after three in a row`() {
        val tracker = tracker().apply { succeeded() }
        tracker.failed("timeout", persistent = false)
        tracker.failed("timeout", persistent = false)
        assertEquals(UpToDate(t), tracker.status.value) // a blip or two doesn't flip the status
        tracker.failed("timeout", persistent = false)
        assertEquals(Error("timeout"), tracker.status.value)

        tracker.succeeded()
        tracker.failed("timeout", persistent = false)
        tracker.failed("timeout", persistent = false)
        assertEquals(UpToDate(t), tracker.status.value) // the success reset the count
    }

    @Test
    fun `labels`() {
        assertEquals("Off", Off.label())
        assertEquals("Importing 120 of 900", Importing(120, 900).label())
        assertEquals("Up to date · last checked 22:13", UpToDate(t).label(TimeZone.UTC))
        assertEquals("Error: x", Error("x").label())
    }

    @Test
    fun `notification prefers errors, then imports, then the idle line`() {
        assertEquals(
            "Email: bad password",
            notificationText(SyncStatuses(sms = Importing(1, 9), email = Error("bad password"))),
        )
        assertEquals("Importing email: 120 of 900", notificationText(SyncStatuses(email = Importing(120, 900))))
        assertEquals("Mac relay: offline", notificationText(SyncStatuses(relay = Error("offline"))))
        assertEquals(
            "Watching SMS & email for transactions",
            notificationText(SyncStatuses(sms = UpToDate(t), email = Off, relay = UpToDate(t))),
        )
    }
}
