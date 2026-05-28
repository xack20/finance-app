package app.hisaab.screens.onboarding

import app.hisaab.AppContainer
import app.hisaab.auth.AuthRepository
import app.hisaab.crypto.CryptoService
import app.hisaab.crypto.MnemonicService
import app.hisaab.platform.BiometricResult
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class OnboardingState(
    val phone: String = "",
    val otpSent: Boolean = false,
    val otpVerified: Boolean = false,
    val biometricEnabled: Boolean = false,
    val recoveryPhrase: List<String> = emptyList(),
    val phraseAcknowledged: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(
    private val container: AppContainer,
    private val authRepository: AuthRepository = container.authRepository,
    private val cryptoService: CryptoService = container.cryptoService,
    private val mnemonicService: MnemonicService = container.mnemonicService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
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

    /** Generates master_secret, prompts for biometric, persists to SecureStorage on success. */
    fun enrollBiometric(onSuccess: () -> Unit, onSkip: () -> Unit) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val secret = cryptoService.generateMasterSecret()
            masterSecret = secret
            val result = container.biometricAuth.authenticate(
                title = "Set up Hisaab",
                subtitle = "Confirm to enable biometric unlock",
            )
            when (result) {
                BiometricResult.Success -> {
                    container.secureStorage.storeMasterSecret(secret)
                    container.secureStorage.storeString("biometric_enabled", "true")
                    _state.update { it.copy(biometricEnabled = true, isLoading = false) }
                    onSuccess()
                }
                BiometricResult.UserCancelled,
                BiometricResult.NotAvailable -> {
                    // Skip-equivalent: persist secret without biometric flag.
                    container.secureStorage.storeMasterSecret(secret)
                    container.secureStorage.storeString("biometric_enabled", "false")
                    _state.update { it.copy(biometricEnabled = false, isLoading = false) }
                    onSkip()
                }
                is BiometricResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    /** Explicit Skip path. Generates secret if needed, persists without biometric. */
    fun skipBiometric(onDone: () -> Unit) {
        scope.launch {
            val secret = masterSecret ?: cryptoService.generateMasterSecret().also { masterSecret = it }
            container.secureStorage.storeMasterSecret(secret)
            container.secureStorage.storeString("biometric_enabled", "false")
            _state.update { it.copy(biometricEnabled = false) }
            onDone()
        }
    }

    fun generateRecoveryPhrase(): List<String> {
        val secret = masterSecret ?: return emptyList()
        val words = mnemonicService.encode(secret)
        _state.update { it.copy(recoveryPhrase = words) }
        return words
    }

    fun acknowledgePhraseWrittenDown() {
        _state.update { it.copy(phraseAcknowledged = true, recoveryPhrase = emptyList()) }
    }

    /** Final onboarding step: open encrypted DB, insert UserProfile, signal complete. */
    fun completeProfile(displayName: String, locale: String, onComplete: () -> Unit) {
        val secret = masterSecret ?: error("master_secret not generated — onboarding flow violated")
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val db = container.openDatabase(secret)
                val userId = randomUuid()
                // Phone number serves as a placeholder for supabase_user_id until full
                // Supabase session integration lands.
                val supabaseUserId = _state.value.phone
                db.hisaabDatabaseQueries.insertUserProfile(
                    id = userId,
                    supabase_user_id = supabaseUserId,
                    display_name = displayName,
                    locale = locale,
                    theme = "auto",
                    created_at = Clock.System.now().toEpochMilliseconds(),
                )
                masterSecret?.fill(0)
                masterSecret = null
                _state.update { it.copy(isLoading = false) }
                onComplete()
            } catch (e: Throwable) {
                _state.update { it.copy(isLoading = false, error = "Setup failed: ${e.message}") }
            }
        }
    }

    fun dispose() {
        masterSecret?.fill(0)
        masterSecret = null
        _state.update { it.copy(recoveryPhrase = emptyList()) }
        scope.cancel()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun randomUuid(): String {
    val bytes = LibsodiumRandom.buf(16).toByteArray()
    val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
}
