package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drain/emit/delete + error-isolation logic of the relay ingest, tested without libsodium by
 * injecting a fake [decode] (the real crypto is covered by RelayCaptureCryptoInstrumentedTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RelayCaptureSourceTest {

    private class FakeTransport(initial: List<RelayRow>) : RelayTransport {
        private val remaining = initial.toMutableList()
        val deleted = mutableListOf<String>()
        override suspend fun fetchPending(): List<RelayRow> = remaining.toList()
        override suspend fun delete(id: String) { deleted += id; remaining.removeAll { it.id == id } }
        override fun changes(): Flow<Unit> = emptyFlow() // completes → observeIncoming finishes after the initial drain
    }

    private fun row(id: String) = RelayRow(id, nonce = ByteArray(24), ciphertext = id.encodeToByteArray())

    /** Pretends the ciphertext bytes are the capture body (real decode is XChaCha20-Poly1305). */
    private val decode: suspend (RelayRow) -> RawCapture? = { r ->
        RawCapture(sender = "bank", body = r.ciphertext.decodeToString(), receivedAt = 1L, channel = CaptureChannel.SMS)
    }

    @Test
    fun drains_emits_and_deletes_each_row() = runTest {
        val t = FakeTransport(listOf(row("a"), row("b")))
        val emitted = RelayCaptureSource(t, decode).observeIncoming().toList()
        assertEquals(listOf("a", "b"), emitted.map { it.body })
        assertEquals(listOf("a", "b"), t.deleted)
    }

    @Test
    fun capabilities_is_sms_and_backfill_is_empty() = runTest {
        val src = RelayCaptureSource(FakeTransport(emptyList()), decode)
        assertEquals(setOf(CaptureChannel.SMS), src.capabilities())
        assertTrue(src.backfillSince(0L).isEmpty())
    }

    private fun tracker() = ChannelStatusTracker(now = { 42L })

    @Test
    fun `relay status is Off when disabled`() = runTest {
        val status = tracker().apply { succeeded() }
        RelayCaptureSource(FakeTransport(listOf(row("a"))), decode, enabled = { false }, status = status)
            .observeIncoming().toList()
        assertEquals(ChannelStatus.Off, status.status.value)
    }

    @Test
    fun `a successful drain reports up to date`() = runTest {
        val status = tracker()
        RelayCaptureSource(FakeTransport(listOf(row("a"))), decode, status = status).observeIncoming().toList()
        assertEquals(ChannelStatus.UpToDate(42L), status.status.value)
    }

    @Test
    fun `three failed fetches in a row surface an error`() = runTest {
        val failing = object : RelayTransport {
            override suspend fun fetchPending(): List<RelayRow> = error("offline")
            override suspend fun delete(id: String) {}
            override fun changes(): Flow<Unit> = flowOf(Unit, Unit) // initial drain + 2 more = 3 failures
        }
        val status = tracker()
        RelayCaptureSource(failing, decode, status = status).observeIncoming().toList()
        assertEquals(ChannelStatus.Error("offline"), status.status.value)
    }

    @Test
    fun undecodable_row_is_skipped_and_left_for_ttl() = runTest {
        val t = FakeTransport(listOf(row("good"), row("bad")))
        val src = RelayCaptureSource(t, decode = { r -> if (r.id == "bad") null else decode(r) })
        val emitted = src.observeIncoming().toList()
        assertEquals(listOf("good"), emitted.map { it.body })
        assertEquals(listOf("good"), t.deleted) // 'bad' is NOT deleted — server TTL reclaims it
    }

    @Test
    fun decode_failure_is_isolated_via_onError() = runTest {
        val t = FakeTransport(listOf(row("boom")))
        val errors = mutableListOf<Throwable>()
        val src = RelayCaptureSource(t, decode = { error("cannot open") }, onError = { errors += it })
        val emitted = src.observeIncoming().toList()
        assertTrue(emitted.isEmpty())
        assertTrue(t.deleted.isEmpty())
        assertEquals(1, errors.size)
    }

    @Test
    fun composite_merges_capabilities_and_backfill() = runTest {
        val a = StubSource(setOf(CaptureChannel.NOTIFICATION), listOf(cap("x")))
        val b = StubSource(setOf(CaptureChannel.SMS), listOf(cap("y")))
        val composite = CompositeCaptureSource(listOf(a, b))
        assertEquals(setOf(CaptureChannel.NOTIFICATION, CaptureChannel.SMS), composite.capabilities())
        assertEquals(listOf("x", "y"), composite.backfillSince(0L).map { it.body })
    }

    private fun cap(body: String) = RawCapture("s", body, 1L, CaptureChannel.SMS)

    private class StubSource(
        private val caps: Set<CaptureChannel>,
        private val backfill: List<RawCapture>,
    ) : CaptureSource {
        override fun capabilities() = caps
        override fun observeIncoming(): Flow<RawCapture> = flowOf(*backfill.toTypedArray())
        override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = backfill
    }
}
