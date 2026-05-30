package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertEquals(50L, source.lastBackfillCursor)
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
    fun `catchUp - item 1 throws but item 2 still backfills and advances the cursor`() = runTest {
        // Backfill-path twin of the live-stream isolation test below: one bad historical SMS must
        // not block the rest of the 90-day import.
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
    fun `runInitialBackfill queries source with now minus 90 days when no cursor stored`() = runTest {
        val now = 10_000_000_000L
        val ninetyDaysMs = 90L * 24L * 60L * 60L * 1000L
        val source = FakeCaptureService().apply {
            backfillRows = listOf(sms("hist-1", now - 1_000), sms("hist-2", now - 500))
        }
        val config = FakeCaptureConfigRepository(initialCursor = 0)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.runInitialBackfill(nowMs = now)

        assertEquals(DEFAULT_BACKFILL_DAYS, 90)                       // constant is the locked default
        assertEquals(now - ninetyDaysMs, source.lastBackfillCursor)  // cursor = now - 90d
        assertEquals(listOf("hist-1", "hist-2"), handler.handled.map { it.body })
        assertEquals(now - 500, config.cursor)
    }

    @Test
    fun `runInitialBackfill uses the stored cursor when it is newer than the 90-day floor`() = runTest {
        val now = 10_000_000_000L
        val storedCursor = now - 1_000   // newer than now-90d → must win
        val source = FakeCaptureService().apply { backfillRows = emptyList() }
        val config = FakeCaptureConfigRepository(initialCursor = storedCursor)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.runInitialBackfill(nowMs = now)

        assertEquals(storedCursor, source.lastBackfillCursor) // never re-imports older than the cursor
    }
}
