package app.hisaab.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeAuthRepository : AuthRepository {
    var otpError: Throwable? = null
    var verifyError: Throwable? = null
    var signedIn: Boolean = false
    private val _events = MutableSharedFlow<AuthEvent>()

    override suspend fun sendOtp(phone: String): Result<Unit> {
        otpError?.let { return Result.failure(it) }
        return Result.success(Unit)
    }

    override suspend fun verifyOtp(phone: String, token: String): Result<Unit> {
        verifyError?.let { return Result.failure(it) }
        signedIn = true
        return Result.success(Unit)
    }

    override fun isSignedIn(): Boolean = signedIn
    override suspend fun signOut() { signedIn = false }
    override fun authEvents(): Flow<AuthEvent> = _events

    suspend fun emitSignedIn() = _events.emit(AuthEvent.SignedIn)
    suspend fun emitSignedOut() = _events.emit(AuthEvent.SignedOut)
}
