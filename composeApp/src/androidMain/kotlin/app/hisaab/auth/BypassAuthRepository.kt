package app.hisaab.auth

import app.hisaab.platform.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * TEMPORARY debug-only auth bypass for emulator verification (no Supabase test OTP configured).
 * Always succeeds; never sends/verifies a real OTP.
 *
 * Persists the signed-in flag in [SecureStorage] so a cold restart skips OTP — mirroring how the
 * real [SupabaseAuthRepository] persists its session token. Without this the in-memory flag reset
 * on every process launch, forcing a full re-onboard from OTP each time.
 *
 * DO NOT COMMIT — revert to [SupabaseAuthRepository] before finishing.
 */
class BypassAuthRepository(private val secureStorage: SecureStorage) : AuthRepository {
    private val events = MutableSharedFlow<AuthEvent>()

    override suspend fun sendOtp(phone: String): Result<Unit> = Result.success(Unit)

    override suspend fun verifyOtp(phone: String, token: String): Result<Unit> {
        secureStorage.storeString(KEY_SIGNED_IN, "true")
        return Result.success(Unit)
    }

    override fun isSignedIn(): Boolean = secureStorage.loadString(KEY_SIGNED_IN) == "true"

    override suspend fun signOut() {
        secureStorage.storeString(KEY_SIGNED_IN, "false")
    }

    override fun authEvents(): Flow<AuthEvent> = events

    private companion object {
        const val KEY_SIGNED_IN = "bypass_signed_in"
    }
}
