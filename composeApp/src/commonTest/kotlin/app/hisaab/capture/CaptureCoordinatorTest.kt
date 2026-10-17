package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureCoordinatorTest {

    /** Records every raw it handled, in order; dedups nothing itself. */
    private class RecordingHandler : CaptureHandler {
        val handled = mutableListOf<RawCapture>()
        override suspend fun handle(raw: RawCapture) { handled.add(raw) }
    }

    private class FakeCaptureConfigRepository(initialCursor: Long) : CaptureCursorStore {
        var cursor: Long = initialCursor
            private set
        override suspend fun currentCursor(): Long = cursor
        override suspend fun advanceCursor(toMs: Long) { cursor = toMs }
    }

    private fun sms(body: String, at: Long, sender: String = "bKash") =
        RawCapture(sender = sender, body = body, receivedAt = at, channel = CaptureChannel.SMS)

    @Test
    fun `catchUp backfills since stored cursor and advances cursor to newest received`() = runTest {
        val source = FakeCaptureService().apply {
            backfillRows = listOf(sms("a", 100), sms("b", 200), sms("c", 300))
        }
        val config = FakeCaptureConfigRepository(initialCursor = 50)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.catchUp()

        assertEquals(0L, source.lastBackfillCursor) // 24 h overlap floors at 0 for a cursor of 50
        assertEquals(listOf("a", "b", "c"), handler.handled.map { it.body })
        assertEquals(300L, config.cursor) // advanced to max receivedAt
    }

    @Test
    fun `start forwards live captures to handler and advances cursor`() =
        runTest(UnconfinedTestDispatcher()) {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val source = FakeCaptureService()
            val config = FakeCaptureConfigRepository(initialCursor = 0)
            val handler = RecordingHandler()
            val coordinator = CaptureCoordinator(source, handler, config, dispatcher = testDispatcher)

            coordinator.start(backgroundScope)

            source.emit(sms("live-1", 500))
            source.emit(sms("live-2", 600))

            assertEquals(listOf("live-1", "live-2"), handler.handled.map { it.body })
            assertEquals(600L, config.cursor)
        }

    @Test
    fun `email and relay-style captures never move the SMS cursor`() =
        runTest(UnconfinedTestDispatcher()) {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val source = FakeCaptureService()
            val config = FakeCaptureConfigRepository(initialCursor = 100)
            val coordinator = CaptureCoordinator(source, RecordingHandler(), config, dispatcher = testDispatcher)
            coordinator.start(backgroundScope)

            source.emit(RawCapture(sender = "bank@x.com", body = "e", receivedAt = 9_000, channel = CaptureChannel.EMAIL))
            assertEquals(100L, config.cursor) // a newer email must not skip SMS still waiting below it

            source.emit(sms("live", 500))
            assertEquals(500L, config.cursor)
        }

    @Test
    fun `catchUp re-reads the 24 hours before the cursor`() = runTest {
        val source = FakeCaptureService()
        CaptureCoordinator(source, RecordingHandler(), FakeCaptureConfigRepository(2 * CATCH_UP_OVERLAP_MS)).catchUp()
        assertEquals(CATCH_UP_OVERLAP_MS, source.lastBackfillCursor)

        CaptureCoordinator(source, RecordingHandler(), FakeCaptureConfigRepository(1_000)).catchUp()
        assertEquals(0L, source.lastBackfillCursor)
    }

    private class FakeImportState : ImportStateStore {
        var pending = false
        override suspend fun isImportPending() = pending
        override suspend fun setImportPending(pending: Boolean) { this.pending = pending }
    }

    @Test
    fun `importAllHistory resumes after being interrupted`() = runTest {
        val source = FakeCaptureService().apply { backfillRows = listOf(sms("a", 100), sms("b", 200)) }
        val store = FakeImportState()
        val dying = object : CaptureHandler {
            override suspend fun handle(raw: RawCapture) {
                if (raw.body == "b") throw kotlinx.coroutines.CancellationException("app locked / killed")
            }
        }
        runCatching { CaptureCoordinator(source, dying, FakeCaptureConfigRepository(0), importState = store).importAllHistory() }
        assertTrue(store.pending) // interrupted mid-import → still owed

        val handler = RecordingHandler()
        CaptureCoordinator(source, handler, FakeCaptureConfigRepository(0), importState = store).resumePendingImport()
        assertEquals(listOf("a", "b"), handler.handled.map { it.body })
        assertEquals(false, store.pending)
    }

    @Test
    fun `a cancelled import is not marked complete even when the rest is already imported`() = runTest {
        val source = FakeCaptureService().apply { backfillRows = listOf(sms("a", 100), sms("b", 200)) }
        val store = FakeImportState()
        // Cancelled during item a; item b is an already-imported duplicate whose handling never suspends.
        val handler = object : CaptureHandler {
            override suspend fun handle(raw: RawCapture) {
                if (raw.body == "a") kotlinx.coroutines.currentCoroutineContext().cancel()
            }
        }
        val coordinator = CaptureCoordinator(source, handler, FakeCaptureConfigRepository(0), importState = store)
        launch { coordinator.importAllHistory() }.join()
        assertTrue(store.pending) // still owed: it was stopped, not finished
    }

    @Test
    fun `a pending SMS import doesn't resume while SMS capture is off`() = runTest {
        val source = FakeCaptureService().apply { backfillRows = listOf(sms("a", 100)) }
        val store = FakeImportState().apply { pending = true }
        CaptureCoordinator(source, RecordingHandler(), FakeCaptureConfigRepository(0), importState = store)
            .resumePendingImport(smsCaptureEnabled = false)
        assertEquals(null, source.lastBackfillCursor)
        assertTrue(store.pending) // resumes once SMS capture is back on
    }

    @Test
    fun `resumePendingImport does nothing when no import is pending`() = runTest {
        val source = FakeCaptureService()
        CaptureCoordinator(source, RecordingHandler(), FakeCaptureConfigRepository(0), importState = FakeImportState())
            .resumePendingImport()
        assertEquals(null, source.lastBackfillCursor)
    }

    @Test
    fun `cursor never moves backward when an older capture arrives`() =
        runTest(UnconfinedTestDispatcher()) {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val source = FakeCaptureService()
            val config = FakeCaptureConfigRepository(initialCursor = 1000)
            val handler = RecordingHandler()
            val coordinator = CaptureCoordinator(source, handler, config, dispatcher = testDispatcher)

            coordinator.start(backgroundScope)

            source.emit(sms("old", 200))       // older than current cursor

            assertTrue("old" in handler.handled.map { it.body }) // still delivered to handler
            assertEquals(1000L, config.cursor)                   // but cursor unchanged
        }

    @Test
    fun `catchUp delivers rows oldest-first regardless of source order`() = runTest {
        val source = FakeCaptureService().apply {
            backfillRows = listOf(sms("newest", 900), sms("oldest", 100), sms("mid", 500))
        }
        val config = FakeCaptureConfigRepository(initialCursor = 0)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.catchUp()

        assertEquals(listOf("oldest", "mid", "newest"), handler.handled.map { it.body })
        assertEquals(900L, config.cursor)
    }

    @Test
    fun `catchUp - a failing handler is isolated, does not crash backfill, and does not advance the cursor`() =
        runTest {
            // Regression for the FK-violation crash: an unguarded backfill let one bad item throw
            // straight out of catchUp() and crash the app on unlock. process() must now isolate it.
            val source = FakeCaptureService().apply { backfillRows = listOf(sms("boom", 400)) }
            val config = FakeCaptureConfigRepository(initialCursor = 10)
            val throwing = CaptureHandler { error("pipeline blew up") }
            val coordinator = CaptureCoordinator(source, throwing, config)

            coordinator.catchUp() // must NOT throw — the failure is isolated per-item

            assertEquals(10L, config.cursor) // cursor unchanged → the bad item is retried next unlock
        }

    @Test
    fun `catchUp - onItemError surfaces an isolated handler failure`() = runTest {
        // The failure is still isolated (cursor not advanced) but is now observable via onItemError,
        // so a persistently-failing item isn't retried invisibly forever.
        val source = FakeCaptureService().apply { backfillRows = listOf(sms("boom", 400)) }
        val config = FakeCaptureConfigRepository(initialCursor = 10)
        val throwing = CaptureHandler { error("pipeline blew up") }
        val errors = mutableListOf<Pair<String, String>>()
        val coordinator = CaptureCoordinator(source, throwing, config) { raw, e ->
            errors.add(raw.body to (e.message ?: ""))
        }

        coordinator.catchUp()

        assertEquals(10L, config.cursor)                          // still isolated → not advanced
        assertEquals(1, errors.size)                              // callback fired exactly once
        assertEquals("boom", errors.single().first)               // with the failed item
        assertEquals("pipeline blew up", errors.single().second)  // and its cause
    }

    @Test
    fun `catchUp - item 1 throws but item 2 still backfills and advances the cursor`() = runTest {
        // Backfill-path twin of the live-stream isolation test below: one bad historical SMS must
        // not block the rest of the history import.
        val config = FakeCaptureConfigRepository(initialCursor = 0)
        val processed = mutableListOf<String>()
        var callCount = 0
        val throwingThenOk = CaptureHandler { raw ->
            callCount++
            if (callCount == 1) error("item 1 failed")
            processed.add(raw.body)
        }
        val source = FakeCaptureService().apply {
            backfillRows = listOf(sms("boom", 400), sms("ok", 500))
        }
        val coordinator = CaptureCoordinator(source, throwingThenOk, config)

        coordinator.catchUp()

        assertEquals(listOf("ok"), processed)  // item 2 imported despite item 1 failing
        assertEquals(500L, config.cursor)      // cursor advanced to the successfully-handled item
    }

    @Test
    fun `start - a handler that throws on item 1 still lets item 2 be processed`() =
        runTest(UnconfinedTestDispatcher()) {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val source = FakeCaptureService()
            val config = FakeCaptureConfigRepository(initialCursor = 0)
            val processed = mutableListOf<String>()
            var callCount = 0
            val throwingThenOk = CaptureHandler { raw ->
                callCount++
                if (callCount == 1) error("item 1 failed")
                processed.add(raw.body)
            }
            val coordinator = CaptureCoordinator(source, throwingThenOk, config, dispatcher = testDispatcher)

            coordinator.start(backgroundScope)

            source.emit(sms("boom", 500))    // first item throws
            source.emit(sms("ok", 600))      // second item must still arrive

            assertEquals(listOf("ok"), processed)  // item 2 processed despite item 1 failure
        }

    @Test
    fun `importAllHistory reads the whole inbox from the beginning even after live capture moved the cursor`() =
        runTest {
            val source = FakeCaptureService().apply {
                backfillRows = listOf(sms("2019", 1_000), sms("2024", 5_000))
            }
            val config = FakeCaptureConfigRepository(initialCursor = 9_000) // live capture already ran
            val handler = RecordingHandler()
            val coordinator = CaptureCoordinator(source, handler, config)

            coordinator.importAllHistory()

            assertEquals(0L, source.lastBackfillCursor) // all time, not "since the cursor"
            assertEquals(listOf("2019", "2024"), handler.handled.map { it.body })
            assertEquals(9_000L, config.cursor) // the live cursor never moves backward
        }

    @Test
    fun `importAllHistory reports progress per message and clears it when done`() = runTest {
        val source = FakeCaptureService().apply {
            backfillRows = listOf(sms("a", 100), sms("b", 200), sms("c", 300))
        }
        lateinit var coordinator: CaptureCoordinator
        val seen = mutableListOf<ImportProgress?>()
        val handler = object : CaptureHandler {
            override suspend fun handle(raw: RawCapture) { seen += coordinator.importProgress.value }
        }
        coordinator = CaptureCoordinator(source, handler, FakeCaptureConfigRepository(initialCursor = 0))

        coordinator.importAllHistory()

        assertEquals<List<ImportProgress?>>(listOf(ImportProgress(0, 3), ImportProgress(1, 3), ImportProgress(2, 3)), seen)
        assertNull(coordinator.importProgress.value)
    }

    @Test
    fun `a second importAllHistory while one is running is ignored`() = runTest {
        val source = FakeCaptureService().apply { backfillRows = listOf(sms("a", 100), sms("b", 200)) }
        val gate = CompletableDeferred<Unit>()
        val handled = mutableListOf<String>()
        val handler = object : CaptureHandler {
            override suspend fun handle(raw: RawCapture) { gate.await(); handled += raw.body }
        }
        val coordinator = CaptureCoordinator(source, handler, FakeCaptureConfigRepository(initialCursor = 0))

        val first = launch { coordinator.importAllHistory() }
        runCurrent() // first import is now parked inside item "a"
        val second = launch { coordinator.importAllHistory() } // e.g. a double tap
        runCurrent()
        gate.complete(Unit)
        first.join(); second.join()

        assertEquals(listOf("a", "b"), handled) // one pass, not two
    }
}
