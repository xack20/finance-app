package app.hisaab.platform

import app.hisaab.capture.CaptureSource
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class CaptureService : CaptureSource {
    actual override fun capabilities(): Set<CaptureChannel> = emptySet()
    actual suspend fun hasSmsPermission(): Boolean = false
    actual suspend fun requestSmsPermission(): Boolean = false
    actual override fun observeIncoming(): Flow<RawCapture> = emptyFlow()
    actual override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = emptyList()
    actual fun openNotificationAccessSettings() { /* no-op on wasm */ }
}
