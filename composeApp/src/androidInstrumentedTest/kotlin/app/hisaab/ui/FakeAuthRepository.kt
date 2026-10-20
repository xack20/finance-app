package app.hisaab.ui

import app.hisaab.auth.AuthEvent
import app.hisaab.auth.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test-only [AuthRepository] for the onboarding E2E: OTP send/verify always succeed, so the real
 * Welcome → OTP → … funnel runs without a live Supabase phone number. Starts signed-out so App()
 * routes to onboarding; verifyOtp flips it signed-in.
 */
class FakeAuthRepository(private var signedIn: Boolean = false) : AuthRepository {

    private val events = MutableStateFlow(if (signedIn) AuthEvent.SignedIn else AuthEvent.SignedOut)

    override suspend fun sendOtp(phone: String): Result<Unit> = Result.success(Unit)

    override suspend fun verifyOtp(phone: String, token: String): Result<Unit> {
        signedIn = true
        events.value = AuthEvent.SignedIn
        return Result.success(Unit)
    }

    override fun isSignedIn(): Boolean = signedIn

    override suspend fun awaitInitialized() = Unit

    override suspend fun signOut() {
        signedIn = false
        events.value = AuthEvent.SignedOut
    }

    override fun authEvents(): Flow<AuthEvent> = events
}
