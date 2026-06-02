package app.hisaab.mac

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

/** PostgREST insert shape for `relay_captures` (base64 text columns; server is zero-knowledge). */
@Serializable
private class RelayInsertRow(
    val user_id: String,
    val device_id: String,
    val nonce: String,
    val ciphertext: String,
)

/**
 * Supabase access for the Mac helper: phone-OTP login (the SAME account as the app, so auth.uid()
 * matches the phone's RLS scope) plus the relay insert. Creates its own client with the project URL
 * + anon key (anon key is public-by-design; RLS protects the rows). OTP calls mirror the app's
 * SupabaseAuthRepository. Not unit-tested (needs a live project) — exercised on a real run.
 */
class SupabaseRelay(supabaseUrl: String, supabaseAnonKey: String) {

    val client: SupabaseClient = createSupabaseClient(supabaseUrl, supabaseAnonKey) {
        install(Auth)
        install(Postgrest)
    }

    suspend fun sendOtp(phone: String) {
        client.auth.signInWith(OTP) { this.phone = phone }
    }

    suspend fun verifyOtp(phone: String, token: String) {
        client.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = phone, token = token)
    }

    fun currentUserId(): String? = client.auth.currentSessionOrNull()?.user?.id

    /** A [RelayUploader] that inserts rows as the signed-in user (RLS enforces user_id = auth.uid()). */
    fun uploader(): RelayUploader = RelayUploader { rows ->
        val uid = currentUserId() ?: error("Not signed in")
        client.postgrest.from("relay_captures").insert(
            rows.map { RelayInsertRow(user_id = uid, device_id = it.deviceId, nonce = it.nonceB64, ciphertext = it.ciphertextB64) },
        )
    }
}
