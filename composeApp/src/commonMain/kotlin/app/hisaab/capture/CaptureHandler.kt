package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow

/**
 * The minimal seam the capture pipeline plugs into. M3-2 ships a no-op (or test) handler;
 * M3-3 wires `CaptureHandler { capturePipeline.process(it) }` — `CapturePipeline.process`
 * is `suspend fun process(raw: RawCapture)`, which is SAM-compatible with this interface.
 */
fun interface CaptureHandler {
    suspend fun handle(raw: RawCapture)
}

/**
 * Common, JVM-instantiable view of a capture source. The platform `CaptureService` (each actual)
 * implements this so [CaptureCoordinator] depends on an interface that can be faked in commonTest
 * (an `expect class` cannot be constructed from commonTest, and there is no JVM actual).
 */
interface CaptureSource {
    fun capabilities(): Set<CaptureChannel>
    fun observeIncoming(): Flow<RawCapture>
    suspend fun backfillSince(cursorMs: Long): List<RawCapture>
}
