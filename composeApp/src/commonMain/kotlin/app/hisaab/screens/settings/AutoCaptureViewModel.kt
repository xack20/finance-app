package app.hisaab.screens.settings

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.SenderRepository
import app.hisaab.domain.BankType
import app.hisaab.domain.CaptureConfig
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import app.hisaab.domain.SenderMapping
import app.hisaab.llm.LlmRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Result of a "Validate" tap on the cloud key field. */
enum class KeyValidation { IDLE, CHECKING, VALID, INVALID }

/** Default auto-post confidence threshold (CaptureConfig schema default). */
const val DEFAULT_AUTO_POST_THRESHOLD: Double = 0.85

/** Number of days in the default historical backfill window. */
private const val BACKFILL_DAYS = 90L
private const val MS_PER_DAY = 24L * 60 * 60 * 1000

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
    /** Runs the coordinator's initial backfill for the given nowMs timestamp. */
    private val runBackfill: suspend (nowMs: Long) -> Unit,
    private val loadApiKey: (key: String) -> String?,
    private val storeApiKey: (key: String, value: String) -> Unit,
    private val clearApiKey: (key: String) -> Unit,
    private val router: LlmRouter,
    /** Starts the capture coordinator (called when captureEnabled transitions to true). */
    private val onStartCapture: () -> Unit = {},
    /** Stops the capture coordinator (called when captureEnabled transitions to false). */
    private val onStopCapture: () -> Unit = {},
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

    private val _keyValidation = MutableStateFlow(KeyValidation.IDLE)
    val keyValidation: StateFlow<KeyValidation> = _keyValidation.asStateFlow()

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
                // M3-int Fix 4: route backfill through coordinator so each RawCapture is processed.
                runBackfill(currentMillis())
            } else {
                _permissionDenied.value = true
                configRepo.setCaptureEnabled(false)
            }
        }
    }

    // ---- Engine ----

    fun setEngineMode(mode: EngineMode) { scope.launch { configRepo.setEngineMode(mode) } }

    fun setCloudProvider(provider: CloudProvider?, model: String?) {
        scope.launch { configRepo.setCloudProvider(provider, model) }
    }

    fun apiKeyFor(provider: CloudProvider): String? = loadApiKey(keyName(provider))

    fun setApiKey(provider: CloudProvider, value: String) {
        storeApiKey(keyName(provider), value)
        _keyValidation.value = KeyValidation.IDLE
    }

    fun clearApiKeyFor(provider: CloudProvider) {
        clearApiKey(keyName(provider))
        _keyValidation.value = KeyValidation.IDLE
    }

    fun validateApiKey() {
        scope.launch {
            _keyValidation.value = KeyValidation.CHECKING
            val provider = router.active()
            _keyValidation.value = when {
                provider == null -> KeyValidation.INVALID
                provider.isAvailable() -> KeyValidation.VALID
                else -> KeyValidation.INVALID
            }
        }
    }

    // ---- Safety ----

    fun setRedaction(enabled: Boolean) { scope.launch { configRepo.setRedaction(enabled) } }
    fun setAlwaysReview(enabled: Boolean) { scope.launch { configRepo.setAlwaysReview(enabled) } }
    fun setAutoPostThreshold(threshold: Double) { scope.launch { configRepo.setAutoPostThreshold(threshold) } }

    /** Restores the advanced confidence slider to the shipped default (0.85). */
    fun resetThresholdToDefault() { scope.launch { configRepo.setAutoPostThreshold(DEFAULT_AUTO_POST_THRESHOLD) } }

    // ---- Consent ----

    fun recordConsent(nowMs: Long = currentMillis()) { scope.launch { configRepo.recordConsent(nowMs) } }
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

    fun backfillLast90Days(nowMs: Long = currentMillis()) {
        // M3-int Fix 4: route through coordinator's runInitialBackfill so each SMS is processed.
        scope.launch { runBackfill(nowMs) }
    }

    private fun cursorFor90Days(nowMs: Long): Long = (nowMs - BACKFILL_DAYS * MS_PER_DAY).coerceAtLeast(0L)

    private fun keyName(provider: CloudProvider): String = "llm_api_key_${provider.name}"

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun currentMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
