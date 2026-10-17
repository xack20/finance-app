package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How far a running history import has got ([done] of [total] messages); null when none is running. */
data class ImportProgress(val done: Int, val total: Int)

/** Catch-up re-reads this far before the cursor: dedup makes the overlap free, and it recovers an SMS that
 *  missed the best-effort live bus while a later one had already moved the cursor past it. */
const val CATCH_UP_OVERLAP_MS: Long = 24L * 60 * 60 * 1000

/** Persists "an all-history import was started but hasn't finished", so a killed process resumes it. */
interface ImportStateStore {
    suspend fun isImportPending(): Boolean
    suspend fun setImportPending(pending: Boolean)
}

/** Default for callers that don't resume imports (tests, iOS): remembers nothing. */
private object NoImportState : ImportStateStore {
    override suspend fun isImportPending() = false
    override suspend fun setImportPending(pending: Boolean) {}
}

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
    private val importState: ImportStateStore = NoImportState,
    /**
     * Observability hook for per-item handler failures. Default no-op preserves the existing
     * fail-isolation behaviour; AppContainer wires this to a log so a persistently-failing item
     * is visible instead of being retried silently forever.
     */
    private val onItemError: (RawCapture, Throwable) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private val importLock = Mutex()

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

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

    /** Drains everything from [CATCH_UP_OVERLAP_MS] before the stored cursor (oldest-first); dedup skips repeats. */
    suspend fun catchUp() {
        drainSince(maxOf(0L, cursorStore.currentCursor() - CATCH_UP_OVERLAP_MS))
    }

    /** Re-runs [importAllHistory] if a previous one was interrupted (app locked, process killed). */
    suspend fun resumePendingImport() {
        if (importState.isImportPending()) importAllHistory()
    }

    /**
     * Imports every message the source holds, all time, oldest-first, publishing [importProgress].
     *
     * Deliberately ignores the live cursor (which may already sit at "now"): the pipeline dedups by
     * `dedup_hash`, so re-running — e.g. after the app locked mid-import and closed the DB — skips what's
     * done and resumes cheaply. [process] still never moves the live cursor backward.
     */
    suspend fun importAllHistory() {
        if (!importLock.tryLock()) return // one already running (e.g. a double tap) — don't queue a second pass
        try {
            importState.setImportPending(true)
            drainSince(0L) { done, total -> _importProgress.value = ImportProgress(done, total) }
            importState.setImportPending(false) // not in finally: an interrupted import stays owed
        } finally {
            _importProgress.value = null
            importLock.unlock()
        }
    }

    private suspend fun drainSince(sinceMs: Long, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val rows = source.backfillSince(sinceMs).sortedBy { it.receivedAt }
        rows.forEachIndexed { index, raw ->
            onProgress(index, rows.size)
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
                onItemError(raw, e) // surface the failure (no-op by default) before isolating it
                return@withLock // isolate the failed item; leave the cursor where it is for retry
            }
            // Only SMS move the SMS cursor: email/relay have their own cursors, and a newer email advancing it
            // would make catch-up skip an SMS that missed the live bus.
            // shortcut: relay-forwarded SMS also carry channel SMS; harmless on Android where the Mac relay
            // isn't used — tag captures by source if that changes.
            if (raw.channel == CaptureChannel.SMS && raw.receivedAt > cursorStore.currentCursor()) {
                cursorStore.advanceCursor(raw.receivedAt)
            }
        }
    }
}
