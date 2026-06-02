package app.hisaab.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeAuthRepository : AuthRepository {
    var otpError: Throwable? = null
    var verifyError: Throwable? = null
    var signedIn: Boolean = false

    /**
     * When true, models supabase-kt restoring a persisted session asynchronously: [isSignedIn]
     * reports false until [awaitInitialized] has completed (so a router that doesn't await the
     * restore races it and falsely sees "signed out").
     */
    var restoresSessionOnInit: Boolean = false
    private var initialized: Boolean = false

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

    override fun isSignedIn(): Boolean = if (restoresSessionOnInit) initialized else signedIn
    override suspend fun signOut() { signedIn = false }
    override fun authEvents(): Flow<AuthEvent> = _events

    override suspend fun awaitInitialized() { initialized = true }

    suspend fun emitSignedIn() = _events.emit(AuthEvent.SignedIn)
    suspend fun emitSignedOut() = _events.emit(AuthEvent.SignedOut)
}
