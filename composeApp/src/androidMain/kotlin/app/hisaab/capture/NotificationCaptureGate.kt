package app.hisaab.capture

/**
 * Process-global on/off switch for the [HisaabNotificationListenerService] forwarding path.
 *
 * The listener is instantiated and bound by the OS, so it cannot read the SQLCipher-encrypted
 * capture config (which is only open while the app is unlocked). Instead the app mirrors the
 * persisted `notificationCaptureEnabled` flag here: AppContainerAndroid sets it from config on
 * `startCapture()` and whenever the Settings toggle flips, and the listener reads it before
 * publishing. Defaults to `false`, so a freshly-started process forwards nothing until the app
 * arms it from the user's saved preference.
 */
object NotificationCaptureGate {
    @Volatile
    var enabled: Boolean = false
}
