package app.hisaab.screens.onboarding

import app.hisaab.auth.AuthRepository
import app.hisaab.crypto.CryptoService
import app.hisaab.crypto.MnemonicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val phone: String = "",
    val otpSent: Boolean = false,
    val otpVerified: Boolean = false,
    val recoveryPhrase: List<String> = emptyList(),
    val phraseAcknowledged: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(
    private val authRepository: AuthRepository,
    private val cryptoServiceProvider: () -> CryptoService = { CryptoService() },
    private val mnemonicServiceProvider: () -> MnemonicService = { MnemonicService() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val cryptoService: CryptoService by lazy { cryptoServiceProvider() }
    private val mnemonicService: MnemonicService by lazy { mnemonicServiceProvider() }
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    private var masterSecret: ByteArray? = null

    fun sendOtp(phone: String) {
        scope.launch {
            _state.update { it.copy(phone = phone, isLoading = true, error = null) }
            authRepository.sendOtp(phone)
                .onSuccess { _state.update { it.copy(otpSent = true, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun verifyOtp(token: String) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.verifyOtp(_state.value.phone, token)
                .onSuccess { _state.update { it.copy(otpVerified = true, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun generateMasterSecret(onGenerated: (ByteArray) -> Unit) {
        val secret = cryptoService.generateMasterSecret()
        masterSecret = secret
        onGenerated(secret)
    }

    fun generateRecoveryPhrase(): List<String> {
        val secret = masterSecret ?: return emptyList()
        val words = mnemonicService.encode(secret)
        _state.update { it.copy(recoveryPhrase = words) }
        return words
    }

    fun acknowledgePhraseWrittenDown() {
        _state.update { it.copy(phraseAcknowledged = true) }
    }

    fun getMasterSecretAndClear(): ByteArray? {
        val secret = masterSecret?.copyOf()
        masterSecret?.fill(0)
        masterSecret = null
        return secret
    }
}
