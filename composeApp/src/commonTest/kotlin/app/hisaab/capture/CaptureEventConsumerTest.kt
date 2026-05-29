package app.hisaab.capture

import app.hisaab.domain.Direction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureEventConsumerTest {

    @Test
    fun `AutoPosted carries the fields the snackbar label needs`() {
        val e: CaptureEvent = CaptureEvent.AutoPosted(
            txnId = "t1",
            candidateId = "c1",
            amount = 500.0,
            sender = "bKash",
            direction = Direction.CREDIT,
        )
        val posted = e as CaptureEvent.AutoPosted
        assertEquals("t1", posted.txnId)
        assertEquals("c1", posted.candidateId)
        assertEquals(500.0, posted.amount)
        assertEquals("bKash", posted.sender)
        assertEquals(Direction.CREDIT, posted.direction)
    }

    @Test
    fun `a MutableSharedFlow with buffer delivers AutoPosted to a collector`() = runTest {
        // Mirrors the AppContainer-owned bus: MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 16).
        val bus = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 16)
        val events: SharedFlow<CaptureEvent> = bus.asSharedFlow()
        val received = ArrayDeque<CaptureEvent>()
        val job = launch { events.collect { received.add(it) } }
        yield()
        bus.tryEmit(
            CaptureEvent.AutoPosted(
                txnId = "t2",
                candidateId = "c2",
                amount = 1200.0,
                sender = "NAGAD",
                direction = Direction.DEBIT,
            ),
        )
        // Allow the collector coroutine to receive the event
        yield()
        job.cancel()
        assertEquals(1, received.size)
        val first = received.first() as CaptureEvent.AutoPosted
        assertEquals("c2", first.candidateId)
        assertEquals("NAGAD", first.sender)
    }
}
