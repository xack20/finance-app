package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/** One encrypted row of the `relay_captures` table (see the P1 SQL migration). */
class RelayRow(val id: String, val nonce: ByteArray, val ciphertext: ByteArray)

/**
 * Transport over the Supabase `relay_captures` table. Abstracted so [RelayCaptureSource]'s
 * drain/emit/delete logic is testable with a fake (the real Postgrest/Realtime impl is
 * [app.hisaab.capture.SupabaseRelayTransport], wired in the AppContainer).
 */
interface RelayTransport {
    /** Encrypted rows currently queued for the signed-in user, oldest-first. */
    suspend fun fetchPending(): List<RelayRow>

    /** Delete a row once its capture has been handed to the pipeline. */
    suspend fun delete(id: String)

    /** Emits whenever new rows may exist (e.g. a Realtime INSERT). May legitimately never emit. */
    fun changes(): Flow<Unit>
}

/**
 * Phone-side ingest for the Mac→phone capture relay
 * (see docs/superpowers/specs/2026-06-02-mac-sms-companion-relay-design.md, P2).
 *
 * Drains queued encrypted rows, [decode]s each to a [RawCapture], emits it into the existing
 * [CaptureCoordinator] (which runs it through [CapturePipeline]), then deletes the row. Because
 * the coordinator processes each emission synchronously, `emit` suspends until the pipeline is
 * done — so the row is deleted only after it has been ingested (at-least-once; the pipeline's
 * dedup_hash unique index makes a re-read after a failed delete harmless).
 *
 * [decode] returns null when the row can't be opened (no master secret yet, or it doesn't
 * authenticate) — such rows are skipped, not deleted, and the server TTL eventually reclaims them.
 * Crypto lives in [decode] (injected) so this source is unit-testable without libsodium.
 */
class RelayCaptureSource(
    private val transport: RelayTransport,
    private val decode: suspend (RelayRow) -> RawCapture?,
    private val onError: (Throwable) -> Unit = {},
) : CaptureSource {

    override fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.SMS)

    /** Draining happens in [observeIncoming] (so rows can be deleted after processing); none here. */
    override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = emptyList()

    override fun observeIncoming(): Flow<RawCapture> = flow {
        drain()                                  // catch up on rows queued while offline
        transport.changes().collect { drain() }  // then react to new inserts
    }

    private suspend fun FlowCollector<RawCapture>.drain() {
        val rows = runCatching { transport.fetchPending() }.getOrElse { onError(it); return }
        for (row in rows) {
            val capture = runCatching { decode(row) }.getOrElse { onError(it); null } ?: continue
            emit(capture) // suspends until the coordinator finishes processing this capture
            runCatching { transport.delete(row.id) }.onFailure { onError(it) }
        }
    }
}
