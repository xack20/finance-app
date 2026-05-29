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
}
