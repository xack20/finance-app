package app.hisaab.capture

import app.hisaab.domain.RawCapture
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-global bridge between OS-instantiated capture components ([SmsBroadcastReceiver],
 * [HisaabNotificationListenerService]) and the [app.hisaab.platform.CaptureService] actual.
 *
 * Mirrors the AppLifecycle SharedFlow idiom. `extraBufferCapacity` lets a receiver `tryEmit`
 * without suspending (receivers run on the main thread with a short window); DROP_OLDEST keeps
 * the live stream a best-effort latency bonus — correctness is guaranteed by cursor catch-up,
 * not by the live bus, per the design's lock-state constraint.
 */
object CaptureBus {
    private val flow = MutableSharedFlow<RawCapture>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val captures: SharedFlow<RawCapture> = flow.asSharedFlow()

    /** Called from receivers/services. Non-blocking; safe on the Android main thread. */
    fun publish(raw: RawCapture) {
        flow.tryEmit(raw)
    }
}
