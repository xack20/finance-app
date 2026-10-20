package app.hisaab

import app.hisaab.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class AppState {
    data object Loading : AppState()
    data object Unauthenticated : AppState()
    data class Onboarding(val step: OnboardingStep) : AppState()
    data object OnboardingKey : AppState()
    data object Locked : AppState()
    data object Authenticated : AppState()
}

enum class OnboardingStep { WELCOME, OTP, BIOMETRIC, RECOVERY_PHRASE, PROFILE }

class AppViewModel(
    private val authRepository: AuthRepository,
    private val hasMasterSecret: () -> Boolean,
    val lockTimeoutMs: Long = 30_000L,
    private val onLock: () -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state

    private var lockJob: Job? = null

    fun init() {
        scope.launch {
            // The auth client restores any persisted session asynchronously. Wait for that to finish
            // before routing, or a cold start races the load and falls through to Unauthenticated —
            // sending a still-signed-in user back to phone-number entry. The timeout is a safety net:
            // if the restore hangs (e.g. it never settles), don't pin the splash forever.
            withTimeoutOrNull(AUTH_INIT_TIMEOUT_MS) { authRepository.awaitInitialized() }
            _state.value = when {
                !authRepository.isSignedIn() -> AppState.Unauthenticated
                !hasMasterSecret() -> AppState.OnboardingKey
                // A persisted master_secret exists, but on a COLD START the SQLCipher DB is not open
                // yet (master_secret is only held in memory while unlocked, and is zeroed on
                // background). Route to Locked so the unlock path actually opens the DB — going
                // straight to Authenticated would render the main graph against a closed DB and crash
                // (requireDb -> "Database not open"). Onboarding completion still goes straight to
                // Authenticated via onOnboardingComplete(), because it opens the DB inline.
                else -> AppState.Locked
            }
        }
    }

    fun onOnboardingComplete() { _state.value = AppState.Authenticated }

    fun onAppBackground() {
        if (_state.value !is AppState.Authenticated) return
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(lockTimeoutMs)
            onLock()
            _state.value = AppState.Locked
        }
    }

    fun onAppForeground() {
        lockJob?.cancel()
        lockJob = null
    }

    fun onBiometricUnlockSuccess() { _state.value = AppState.Authenticated }

    private companion object {
        // Upper bound on waiting for the auth session restore before routing anyway.
        const val AUTH_INIT_TIMEOUT_MS = 5_000L
    }
}
