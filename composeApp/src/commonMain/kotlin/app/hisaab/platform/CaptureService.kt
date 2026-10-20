package app.hisaab.platform

import app.hisaab.capture.CaptureSource
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow

/**
 * Platform capture layer. Android = SMS receiver + ContentResolver backfill + NotificationListener
 * fallback; iOS = paste/share (PASTE, wired in a later slice); wasm = no capabilities.
 *
 * Implements [CaptureSource] so the channel-agnostic [app.hisaab.capture.CaptureCoordinator] can
 * collect from it without knowing which platform produced a capture.
 */
expect class CaptureService : CaptureSource {
    override fun capabilities(): Set<CaptureChannel>
    suspend fun hasSmsPermission(): Boolean
    suspend fun requestSmsPermission(): Boolean
    override fun observeIncoming(): Flow<RawCapture>
    override suspend fun backfillSince(cursorMs: Long): List<RawCapture>
    fun openNotificationAccessSettings()
    /** True if the user has granted notification access to the listener service (Android only). */
    fun hasNotificationAccess(): Boolean
    /** Arm/disarm the OS-bound notification listener's forwarding gate (Android only; no-op elsewhere). */
    fun setNotificationListenerEnabled(enabled: Boolean)
    /** Background sync shows its status in a notification; Android 13+ needs runtime permission (true elsewhere). */
    fun hasNotificationPermission(): Boolean
    suspend fun requestNotificationPermission(): Boolean
    /** True when the OS won't kill background sync for battery (Android battery-optimization exemption). */
    /** Contacts let the capture agent name the person in "Sent Tk 500 to 017…" (Android; false elsewhere). */
    fun hasContactsPermission(): Boolean
    suspend fun requestContactsPermission(): Boolean
    fun isBackgroundRunningAllowed(): Boolean
    fun openBackgroundRunningSettings()
}
