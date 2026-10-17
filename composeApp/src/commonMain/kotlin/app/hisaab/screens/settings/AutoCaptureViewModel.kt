package app.hisaab.screens.settings

import app.hisaab.capture.ImportProgress
import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.SenderRepository
import app.hisaab.domain.BankType
import app.hisaab.domain.CaptureConfig
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import app.hisaab.domain.SenderMapping
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import app.hisaab.llm.LlmRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Result of a "Validate" tap on the cloud key field; [Invalid] says why, in user-facing words. */
sealed interface KeyValidation {
    data object Idle : KeyValidation
    data object Checking : KeyValidation
    data object Valid : KeyValidation
    data class Invalid(val reason: String) : KeyValidation
}

/** Default auto-post confidence threshold (CaptureConfig schema default). */
const val DEFAULT_AUTO_POST_THRESHOLD: Double = 0.85

/**
 * Drives the Settings → Auto-capture surface. Pure-Kotlin; platform ops (SMS permission,
 * backfill, secure-storage key I/O) are injected as functional seams so the VM is unit-testable
 * on the JVM without an Android CaptureService/SecureStorage.
 */
class AutoCaptureViewModel(
    private val configRepo: CaptureConfigRepository,
    private val senderRepo: SenderRepository,
    private val accountRepo: AccountRepository,
    private val hasSmsPermission: suspend () -> Boolean,
    private val requestSmsPermission: suspend () -> Boolean,
    /** Starts the all-time history import in the app's capture scope, so leaving this screen can't cancel it. */
    private val startHistoryImport: () -> Unit,
    private val loadApiKey: (key: String) -> String?,
    private val storeApiKey: (key: String, value: String) -> Unit,
    private val clearApiKey: (key: String) -> Unit,
    private val router: LlmRouter,
    /** Starts the capture coordinator (called when captureEnabled transitions to true). */
    private val onStartCapture: () -> Unit = {},
    /** Stops the capture coordinator (called when captureEnabled transitions to false). */
    private val onStopCapture: () -> Unit = {},
    /** Android-only: mirror the notification flag to the OS-bound listener's process-global gate. */
    private val onSetNotificationGate: (Boolean) -> Unit = {},
    /** Progress of a running SMS / email history import (null when idle) — shown on the screen. */
    val smsImportProgress: StateFlow<ImportProgress?> = MutableStateFlow(null),
    val emailImportProgress: StateFlow<ImportProgress?> = MutableStateFlow(null),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val config: StateFlow<CaptureConfig?> = configRepo.observe()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    val senders: StateFlow<List<SenderMapping>> = senderRepo.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "New sender detected" prompts: financial senders with no mapped account yet. */
    val unmappedSenders: StateFlow<List<SenderMapping>> = senderRepo.observeAll()
        .map { all -> all.filter { it.isFinancial && it.accountId == null } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts = accountRepo.observeActive()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _keyValidation = MutableStateFlow<KeyValidation>(KeyValidation.Idle)
    val keyValidation: StateFlow<KeyValidation> = _keyValidation.asStateFlow()

    /** Providers with a stored API key — observable so the Validate button enables right after Save. */
    private val _savedKeys = MutableStateFlow(
        CloudProvider.entries.filter { !loadApiKey(keyName(it)).isNullOrBlank() }.toSet(),
    )
    val savedKeys: StateFlow<Set<CloudProvider>> = _savedKeys.asStateFlow()

    private val _permissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = _permissionDenied.asStateFlow()

    // ---- Master toggle ----

    fun setCaptureEnabled(enabled: Boolean) {
        scope.launch {
            if (!enabled) {
                configRepo.setCaptureEnabled(false)
                onStopCapture()
                return@launch
            }
            val granted = if (hasSmsPermission()) true else requestSmsPermission()
            if (granted) {
                configRepo.setCaptureEnabled(true)
                _permissionDenied.value = false
                onStartCapture()
                startHistoryImport()
            } else {
                _permissionDenied.value = true
                configRepo.setCaptureEnabled(false)
            }
        }
    }

    /**
     * Mac companion (P2): toggle pulling encrypted captures from the Mac→phone relay. Enabling
     * starts the coordinator (idempotent); the [RelayCaptureSource][app.hisaab.capture.RelayCaptureSource]
     * activates only once a master secret + session also exist. Disabling lets it go inert (the
     * coordinator keeps running if native capture is still on).
     */
    fun setRelayCaptureEnabled(enabled: Boolean) {
        scope.launch {
            configRepo.setRelayCaptureEnabled(enabled)
            if (enabled) onStartCapture()
        }
    }

    /** EP3: connect a mailbox for in-app IMAP email capture (Android). Stores creds in SecureStorage. */
    fun configureEmail(email: String, appPassword: String) {
        scope.launch {
            val trimmed = email.trim()
            if (app.hisaab.capture.ImapHosts.forEmail(trimmed) == null) return@launch // unsupported provider
            storeApiKey("imap_email", trimmed)
            // Gmail displays app passwords in groups of 4 ("abcd efgh …"); the spaces break IMAP login.
            storeApiKey("imap_password", appPassword.filterNot(Char::isWhitespace))
            // No cursor is seeded: the email source starts from the oldest bank email (all-time history).
            configRepo.setEmailCaptureEnabled(true)
            onStartCapture()
        }
    }

    fun setEmailCaptureEnabled(enabled: Boolean) {
        scope.launch {
            configRepo.setEmailCaptureEnabled(enabled)
            if (enabled) onStartCapture()
        }
    }

    /**
     * Notification fallback (Android): persist the flag, flip the OS-bound listener's gate (which
     * can't read the encrypted config), and start the coordinator when enabling. The user still has
     * to grant notification access — see [AutoCaptureScreen]'s "Grant notification access" button.
     */
    fun setNotificationCaptureEnabled(enabled: Boolean) {
        scope.launch {
            configRepo.setNotificationCaptureEnabled(enabled)
            onSetNotificationGate(enabled)
            if (enabled) onStartCapture()
        }
    }

    // ---- Engine ----

    fun setEngineMode(mode: EngineMode) { scope.launch { configRepo.setEngineMode(mode) } }

    fun setCloudProvider(provider: CloudProvider?, model: String?) {
        scope.launch { configRepo.setCloudProvider(provider, model) }
    }

    fun setApiKey(provider: CloudProvider, value: String) {
        val key = value.trim() // pasted keys often carry a trailing newline, which is illegal in an HTTP header
        storeApiKey(keyName(provider), key)
        _savedKeys.update { if (key.isBlank()) it - provider else it + provider }
        _keyValidation.value = KeyValidation.Idle
    }

    fun clearApiKeyFor(provider: CloudProvider) {
        clearApiKey(keyName(provider))
        _savedKeys.update { it - provider }
        _keyValidation.value = KeyValidation.Idle
    }

    /** Checks consent and key presence locally, then asks the provider itself whether it accepts the key. */
    fun validateApiKey() {
        scope.launch {
            _keyValidation.value = KeyValidation.Checking
            if (configRepo.get().cloudConsentAt == null) {
                _keyValidation.value = KeyValidation.Invalid("Agree to cloud processing first (Cloud consent).")
                return@launch
            }
            val provider = router.active() ?: run {
                _keyValidation.value = KeyValidation.Invalid("Save a key for this provider first.")
                return@launch
            }
            _keyValidation.value = try {
                provider.verifyKey()
                KeyValidation.Valid
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmException) {
                KeyValidation.Invalid(reasonFor(e.error))
            } catch (e: Exception) {
                KeyValidation.Invalid("Couldn't reach the provider — check your connection.")
            }
        }
    }

    private fun reasonFor(error: LlmError): String = when (error) {
        LlmError.InvalidKey -> "Key rejected — check it was copied in full."
        LlmError.RateLimited -> "Rate-limited — try again in a moment."
        LlmError.Unavailable -> "Provider unavailable — try again later."
        is LlmError.Network -> "Couldn't reach the provider — check your connection."
        is LlmError.ProviderError -> error.detail
        is LlmError.Decode -> error.message
        is LlmError.Refused -> error.message
    }

    // ---- Safety ----

    fun setRedaction(enabled: Boolean) { scope.launch { configRepo.setRedaction(enabled) } }
    fun setAlwaysReview(enabled: Boolean) { scope.launch { configRepo.setAlwaysReview(enabled) } }
    fun setAutoPostThreshold(threshold: Double) { scope.launch { configRepo.setAutoPostThreshold(threshold) } }

    /** Restores the advanced confidence slider to the shipped default (0.85). */
    fun resetThresholdToDefault() { scope.launch { configRepo.setAutoPostThreshold(DEFAULT_AUTO_POST_THRESHOLD) } }

    // ---- Consent ----

    /**
     * Persists consent, then runs [then] (e.g. navigate up). Callers must leave the screen in [then], not
     * right after this call: leaving disposes the VM and cancels [scope], dropping a not-yet-run write.
     */
    fun recordConsent(nowMs: Long = currentMillis(), then: () -> Unit = {}) {
        scope.launch { configRepo.recordConsent(nowMs); then() }
    }
    fun revokeConsent() { scope.launch { configRepo.clearConsent() } }

    // ---- Senders ----

    fun addSender(senderId: String, displayName: String, bankType: BankType) {
        scope.launch {
            senderRepo.upsert(
                SenderMapping(
                    id = randomId(),
                    senderId = senderId,
                    displayName = displayName,
                    bankType = bankType,
                    isFinancial = true,
                    templateKey = null,
                    accountId = null,
                    createdAt = currentMillis(),
                ),
            )
        }
    }

    fun setSenderAccount(senderId: String, accountId: String) {
        scope.launch { senderRepo.setAccount(senderId, accountId) }
    }

    fun setSenderEnabled(senderId: String, enabled: Boolean) {
        scope.launch {
            val existing = senderRepo.findBySenderId(senderId) ?: return@launch
            senderRepo.upsert(existing.copy(isFinancial = enabled))
        }
    }

    // ---- Backfill ----

    fun importAllHistory() = startHistoryImport()

    private fun keyName(provider: CloudProvider): String = "llm_api_key_${provider.name}"

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun currentMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

    /** Cancel background work when the screen is disposed (wired from AutoCaptureScreen / consent route). */
    fun dispose() {
        scope.cancel()
    }
}
