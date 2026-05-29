package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** JVM-instantiable fake implementing only the channel-agnostic [CaptureSource] surface. */
class FakeCaptureService : CaptureSource {
    private val incoming = MutableSharedFlow<RawCapture>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Rows returned by [backfillSince]; the coordinator should receive them oldest-first. */
    var backfillRows: List<RawCapture> = emptyList()

    /** Records the cursor value [backfillSince] was called with. */
    var lastBackfillCursor: Long? = null
        private set

    override fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.SMS)

    override fun observeIncoming(): Flow<RawCapture> = incoming

    override suspend fun backfillSince(cursorMs: Long): List<RawCapture> {
        lastBackfillCursor = cursorMs
        return backfillRows
    }

    /** Test driver: push a live capture into the stream. */
    fun emit(raw: RawCapture) { incoming.tryEmit(raw) }
}
