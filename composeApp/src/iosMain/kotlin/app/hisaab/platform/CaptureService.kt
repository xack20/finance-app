package app.hisaab.platform

import app.hisaab.capture.CaptureSource
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class CaptureService : CaptureSource {
    actual override fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.PASTE)
    actual suspend fun hasSmsPermission(): Boolean = false
    actual suspend fun requestSmsPermission(): Boolean = false
    actual override fun observeIncoming(): Flow<RawCapture> = emptyFlow()
    actual override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = emptyList()
    actual fun openNotificationAccessSettings() { /* no-op on iOS */ }
    actual fun hasNotificationAccess(): Boolean = false // iOS has no notification-listener API
    actual fun setNotificationListenerEnabled(enabled: Boolean) { /* no-op on iOS */ }
    // iOS has no background capture service, so there's nothing to ask for.
    actual fun hasNotificationPermission(): Boolean = true
    actual suspend fun requestNotificationPermission(): Boolean = true
    actual fun hasContactsPermission(): Boolean = false
    actual suspend fun requestContactsPermission(): Boolean = false
    actual fun isBackgroundRunningAllowed(): Boolean = true
    actual fun openBackgroundRunningSettings() { /* no-op on iOS */ }
}
