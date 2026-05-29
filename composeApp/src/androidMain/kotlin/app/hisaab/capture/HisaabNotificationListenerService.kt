package app.hisaab.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture

/**
 * Fallback capture path for when SMS permission is denied/rejected. Maps posted notifications from
 * the system Messaging surface into [RawCapture]s (channel = NOTIFICATION) and publishes them on
 * [CaptureBus]. The pipeline's pre-filter (M3-3) drops non-financial notifications, so this service
 * forwards permissively and lets the pure pre-filter do classification.
 *
 * Bound by the OS via BIND_NOTIFICATION_LISTENER_SERVICE; requires user-granted notification access.
 */
class HisaabNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return
        if (text.isBlank()) return
        val raw = RawCapture(
            sender = title.ifBlank { sbn.packageName },
            body = text,
            receivedAt = sbn.postTime,
            channel = CaptureChannel.NOTIFICATION,
        )
        CaptureBus.publish(raw)
    }

    // We never react to removals — captures are one-shot at post time.
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }
}
