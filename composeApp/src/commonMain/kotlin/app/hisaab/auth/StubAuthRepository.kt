package app.hisaab.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No-op repository used until Supabase credentials are wired and proper DI
 * is added. Always reports not-signed-in so the onboarding flow runs.
 */
class StubAuthRepository : AuthRepository {
    override suspend fun sendOtp(phone: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyOtp(phone: String, token: String): Result<Unit> = Result.success(Unit)
    override fun isSignedIn(): Boolean = false
    override suspend fun signOut() {}
    override fun authEvents(): Flow<AuthEvent> = emptyFlow()
}
