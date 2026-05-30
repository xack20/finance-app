package app.hisaab.capture

import app.hisaab.domain.RawCapture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Default historical import window applied on first permission grant (spec §2, §6). */
const val DEFAULT_BACKFILL_DAYS: Int = 90

/** Milliseconds in one day. */
private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

/**
 * Narrow cursor seam. M3-1's [app.hisaab.data.CaptureConfigRepository] is adapted to this in
 * AppContainer; unit tests use a fake. Keeps the coordinator free of any DB dependency.
 */
interface CaptureCursorStore {
    suspend fun currentCursor(): Long
    suspend fun advanceCursor(toMs: Long)
}

/**
 * Alive while the DB is open. Collects live captures from [source] and runs cursor-based
 * [catchUp]. Every capture is funneled through a single [Mutex] so live + catch-up never
 * interleave a half-written candidate, and the cursor only ever moves forward.
 *
 * Dedup-by-handler: the coordinator does not dedup; it forwards every capture to [handler],
 * which owns idempotency (M3-3's pipeline dedups by `dedup_hash`). The coordinator only
 * guarantees serialized delivery and monotonic cursor advance.
 */
class CaptureCoordinator(
    private val source: CaptureSource,
    private val handler: CaptureHandler,
    private val cursorStore: CaptureCursorStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()

    /**
     * Starts collecting the live stream on [scope] using [dispatcher] (default: [Dispatchers.Default]
     * so the collection loop and [process] — which includes DB writes via [CaptureCursorStore.advanceCursor]
     * and the M3-3 pipeline — never run on the main thread). Tests inject an [UnconfinedTestDispatcher]
     * to subscribe eagerly and drive virtual time deterministically.
     */
    /**
     * Starts collecting the live stream on [scope] using [dispatcher].
     *
     * Per-item failure isolation lives in [process] (single source of truth), so a handler failure
     * on any single live item is caught there: the cursor does NOT advance for the failed item, and
     * the collection coroutine is NOT cancelled — subsequent items keep flowing. Only coroutine
     * cancellation propagates out of [process], which correctly tears down this collector.
     */
    fun start(scope: CoroutineScope) {
        scope.launch(dispatcher) {
            source.observeIncoming().collect { raw ->
                process(raw)
            }
        }
    }

    /** Drains everything newer than the stored cursor (oldest-first) and advances the cursor. */
    suspend fun catchUp() {
        val since = cursorStore.currentCursor()
        drainSince(since)
    }

    /**
     * One-time historical import on first permission grant. Computes a cursor of
     * `nowMs - DEFAULT_BACKFILL_DAYS days`, imports every SMS newer than that, and advances the
     * stored cursor through the same monotonic, mutex-serialized path as live/catch-up.
     *
     * M3-5's Settings master toggle invokes this together with `setCaptureEnabled(true)` the moment
     * SMS permission is granted; this slice ships the seam (the UX wiring lands in M3-5). Because the
     * cursor only moves forward, re-invoking this after the first run is a no-op for already-imported
     * messages (the floor is `nowMs - 90d`, but [process] never moves the cursor backward).
     */
    suspend fun runInitialBackfill(nowMs: Long) {
        val floor = nowMs - DEFAULT_BACKFILL_DAYS * MILLIS_PER_DAY
        val since = maxOf(floor, cursorStore.currentCursor())
        drainSince(since)
    }

    private suspend fun drainSince(sinceMs: Long) {
        val rows = source.backfillSince(sinceMs).sortedBy { it.receivedAt }
        for (raw in rows) {
            process(raw)
        }
    }

    /**
     * Serialized, fail-isolated per-item processing — the single guard point for BOTH the live
     * collector ([start]) AND the backfill/catch-up path ([drainSince]).
     *
     * A handler failure on one item (malformed SMS, a transient DB/FK error) is caught and the
     * cursor is NOT advanced, so that item is retried on the next catchUp/unlock and a single bad
     * item can never crash backfill or kill the live stream. Coroutine cancellation always
     * propagates so the collector tears down cleanly.
     */
    private suspend fun process(raw: RawCapture) {
        mutex.withLock {
            try {
                handler.handle(raw)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // always re-throw cancellation
            } catch (e: Throwable) {
                return@withLock // isolate the failed item; leave the cursor where it is for retry
            }
            if (raw.receivedAt > cursorStore.currentCursor()) {
                cursorStore.advanceCursor(raw.receivedAt)
            }
        }
    }
}
