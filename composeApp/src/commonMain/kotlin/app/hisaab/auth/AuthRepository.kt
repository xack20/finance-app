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
}
