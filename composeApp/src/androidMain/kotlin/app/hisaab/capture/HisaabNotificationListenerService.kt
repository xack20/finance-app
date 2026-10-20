package app.hisaab.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture

/**
 * Fallback capture path for when SMS permission is denied/rejected. Maps posted notifications from
 * the system Messaging surface into [RawCapture]s (channel = NOTIFICATION) and publishes them on
 * [CaptureBus]. Only notifications that mention money are forwarded ([shouldCaptureNotification]): everything
 * forwarded is read by the cloud capture agent, so chats and Hisaab's own status must stay on the phone.
 *
 * Bound by the OS via BIND_NOTIFICATION_LISTENER_SERVICE; requires user-granted notification access.
 */
class HisaabNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Inert until the user enables notification capture (the OS keeps the listener bound
        // regardless of our preference). NotificationCaptureGate mirrors the persisted config flag.
        if (!NotificationCaptureGate.enabled) return
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return
        if (text.isBlank()) return
        val flags = notification.flags
        val ongoing = flags and (
            Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY
            ) != 0
        if (!shouldCaptureNotification(sbn.packageName, packageName, ongoing, "$title $text")) return
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
