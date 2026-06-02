package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * Fans several [CaptureSource]s into one so the single-source [CaptureCoordinator] can drive them
 * together through its one serializing [kotlinx.coroutines.sync.Mutex]. Used to run the platform
 * SMS/notification source alongside the [RelayCaptureSource] when the Mac companion is enabled.
 *
 * Live streams are merged; backfill concatenates each source's rows (the coordinator re-sorts by
 * timestamp). Note: the coordinator advances its single cursor by each processed capture's
 * `receivedAt` regardless of source — fine for the iOS target (no platform SMS source) and
 * dedup-protected on Android; a per-source cursor split is deferred to P4 if needed.
 */
class CompositeCaptureSource(private val sources: List<CaptureSource>) : CaptureSource {

    override fun capabilities(): Set<CaptureChannel> =
        sources.flatMap { it.capabilities() }.toSet()

    override fun observeIncoming(): Flow<RawCapture> =
        merge(*sources.map { it.observeIncoming() }.toTypedArray())

    override suspend fun backfillSince(cursorMs: Long): List<RawCapture> =
        sources.flatMap { it.backfillSince(cursorMs) }
}
