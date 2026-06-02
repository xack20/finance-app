package app.hisaab.mac

import app.hisaab.capture.RelayCaptureCrypto
import app.hisaab.domain.RawCapture
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** One encrypted row to insert into `relay_captures` (base64 text columns). */
data class RelayUpload(val deviceId: String, val nonceB64: String, val ciphertextB64: String)

/** Insert seam over Supabase; real impl is [SupabaseRelay.uploader], fakeable in tests. */
fun interface RelayUploader {
    suspend fun upload(rows: List<RelayUpload>)
}

/**
 * Reads new forwarded SMS, seals each with the shared [RelayCaptureCrypto] (byte-identical to what
 * the phone opens), and uploads the ciphertext to the relay. Pure orchestration — the chat.db read,
 * the Supabase insert, and cursor persistence are injected, so it's unit-testable end-to-end.
 */
@OptIn(ExperimentalEncodingApi::class)
class RelayPusher(
    private val relayCrypto: RelayCaptureCrypto,
    private val relayKey: ByteArray,
    private val deviceId: String,
    /** Reads captures with Apple-epoch date > cursor; returns (captures, advancedCursor). */
    private val readSince: (cursor: Long) -> Pair<List<RawCapture>, Long>,
    private val uploader: RelayUploader,
    private val loadCursor: () -> Long,
    private val saveCursor: (Long) -> Unit,
) {
    /** Read → seal → upload (in bounded chunks) → advance cursor. Returns how many were pushed. */
    suspend fun pushOnce(): Int {
        val cursor = loadCursor()
        val (captures, newCursor) = readSince(cursor)
        // Upload in bounded chunks so a large backfill never POSTs one oversized batch — the relay
        // would reject it, the cursor would never advance, and the same batch would retry forever.
        for (chunk in captures.chunked(UPLOAD_CHUNK)) {
            uploader.upload(
                chunk.map { cap ->
                    val sealed = relayCrypto.seal(cap, relayKey)
                    RelayUpload(deviceId, Base64.encode(sealed.nonce), Base64.encode(sealed.ciphertext))
                },
            )
        }
        // Advance only after every chunk uploaded: if any upload throws we never reach here, the cursor
        // stays put, and the pass is retried next tick (the phone dedups any rows that did land).
        saveCursor(newCursor)
        return captures.size
    }

    private companion object {
        /** Max rows per relay insert — bounds the POST body for large backfills. */
        const val UPLOAD_CHUNK = 100
    }
}
