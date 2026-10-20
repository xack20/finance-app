package app.hisaab.auth

import kotlinx.coroutines.flow.Flow

sealed class AuthEvent {
    data object SignedIn : AuthEvent()
    data object SignedOut : AuthEvent()
}

interface AuthRepository {
    suspend fun sendOtp(phone: String): Result<Unit>
    suspend fun verifyOtp(phone: String, token: String): Result<Unit>
    fun isSignedIn(): Boolean
    suspend fun signOut()
    fun authEvents(): Flow<AuthEvent>

    /**
     * Suspends until the auth client has finished restoring any persisted session from storage.
     * Must be awaited before [isSignedIn] at cold start: the session is loaded asynchronously, so a
     * synchronous [isSignedIn] can race the load and report a false "signed out" — which would send a
     * still-signed-in user back to phone-number entry.
     */
    suspend fun awaitInitialized()
}
