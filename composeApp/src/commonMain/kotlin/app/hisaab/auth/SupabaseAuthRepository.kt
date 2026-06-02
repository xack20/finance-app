package app.hisaab.auth

import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SupabaseAuthRepository : AuthRepository {

    private val auth get() = supabaseClient.auth

    override suspend fun sendOtp(phone: String): Result<Unit> = runCatching {
        auth.signInWith(OTP) { this.phone = phone }
    }

    override suspend fun verifyOtp(phone: String, token: String): Result<Unit> = runCatching {
        auth.verifyPhoneOtp(
            type = OtpType.Phone.SMS,
            phone = phone,
            token = token,
        )
    }

    override fun isSignedIn(): Boolean = auth.currentSessionOrNull() != null

    override suspend fun awaitInitialized() {
        // sessionStatus starts as Initializing while the persisted session is loaded (and refreshed)
        // from storage, then settles to Authenticated / NotAuthenticated. Waiting for it to leave
        // Initializing makes the subsequent currentSessionOrNull()/isSignedIn() check reliable.
        auth.sessionStatus.first { it !is SessionStatus.Initializing }
    }

    override suspend fun signOut() {
        auth.signOut(SignOutScope.LOCAL)
    }

    override fun authEvents(): Flow<AuthEvent> =
        auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> AuthEvent.SignedIn
                else -> AuthEvent.SignedOut
            }
        }
}
