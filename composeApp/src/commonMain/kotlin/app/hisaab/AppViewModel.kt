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
            _state.value = when {
                !authRepository.isSignedIn() -> AppState.Unauthenticated
                !hasMasterSecret() -> AppState.OnboardingKey
                else -> AppState.Authenticated
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
}
