package app.hisaab.capture

import app.hisaab.auth.supabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/** PostgREST row shape for `relay_captures`; nonce/ciphertext are base64 text. */
@Serializable
private class RelayCaptureRow(val id: String, val nonce: String, val ciphertext: String)

/**
 * Supabase-backed [RelayTransport] over the `relay_captures` table (P2). Row-level security scopes
 * every query to the signed-in user, so no explicit user_id filter is needed — `select` returns
 * only this user's rows and `delete` can only remove them.
 *
 * P2 uses a simple interval poll for [changes] (the Postgrest select/delete are the only API surface
 * we rely on here). Realtime push (lower latency) is a P4 enhancement.
 */
@OptIn(ExperimentalEncodingApi::class)
class SupabaseRelayTransport(
    private val client: SupabaseClient = supabaseClient,
    private val table: String = "relay_captures",
    private val pollIntervalMs: Long = 20_000L,
) : RelayTransport {

    override suspend fun fetchPending(): List<RelayRow> =
        client.postgrest.from(table)
            .select { order("created_at", Order.ASCENDING) }
            .decodeList<RelayCaptureRow>()
            .map { RelayRow(id = it.id, nonce = Base64.decode(it.nonce), ciphertext = Base64.decode(it.ciphertext)) }

    override suspend fun delete(id: String) {
        client.postgrest.from(table).delete { filter { eq("id", id) } }
    }

    /** Ticks on a fixed interval to trigger a re-drain while capture is active. */
    override fun changes(): Flow<Unit> = flow {
        while (true) {
            delay(pollIntervalMs)
            emit(Unit)
        }
    }
}
