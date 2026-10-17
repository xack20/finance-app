package app.hisaab

import android.content.Context
import androidx.fragment.app.FragmentActivity
import app.hisaab.agent.AgentRuntime
import app.hisaab.auth.AuthRepository
import app.hisaab.capture.CaptureCoordinator
import app.hisaab.capture.CaptureEvent
import app.hisaab.crypto.BlobCrypto
import app.hisaab.crypto.CryptoService
import app.hisaab.crypto.MnemonicService
import app.hisaab.data.AccountRepository
import app.hisaab.data.AttachmentRepository
import app.hisaab.data.BudgetRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.ConversationRepository
import app.hisaab.data.InsightRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.DatabaseDriverFactory
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.NewTransaction
import app.hisaab.engine.HisaabEngine
import app.hisaab.llm.LlmRouter
import app.hisaab.platform.AndroidSpeechToText
import app.hisaab.platform.AppLifecycle
import app.hisaab.platform.AudioRecorder
import app.hisaab.platform.BiometricAuth
import app.hisaab.platform.CaptureService
import app.hisaab.platform.CloudTranscriber
import app.hisaab.platform.ContactPicker
import app.hisaab.platform.CrashLog
import app.hisaab.platform.ImagePicker
import app.hisaab.platform.PlatformFileStore
import app.hisaab.platform.SecureStorage
import app.hisaab.platform.SpeechToText
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-Activity DI root. Holds only what needs the Activity (biometric prompt, pickers, permission launchers,
 * speech); the DB and capture engine live in the process-scoped [HisaabEngine] so background capture and the
 * UI share one DB driver. [authRepository] is injectable for instrumented E2E tests (applies on the engine's
 * first creation).
 */
actual class AppContainer(
    private val context: Context,
    activity: FragmentActivity,
    authRepository: AuthRepository? = null,
) {
    private val engine = HisaabEngine.get(context, authRepository)

    actual val biometricAuth: BiometricAuth = BiometricAuth(activity)
    actual val contactPicker: ContactPicker = ContactPicker(activity)
    actual val imagePicker: ImagePicker = ImagePicker(activity)
    actual val lifecycle: AppLifecycle = AppLifecycle()
    actual val captureService: CaptureService = CaptureService(context, activity)
    actual val speechToText: SpeechToText = AndroidSpeechToText(activity)

    actual val secureStorage: SecureStorage get() = engine.secureStorage
    actual val fileStore: PlatformFileStore get() = engine.fileStore
    actual val cryptoService: CryptoService get() = engine.cryptoService
    actual val mnemonicService: MnemonicService get() = engine.mnemonicService
    actual val blobCrypto: BlobCrypto get() = engine.blobCrypto
    actual val authRepository: AuthRepository get() = engine.authRepository
    actual val databaseDriverFactory: DatabaseDriverFactory get() = engine.databaseDriverFactory

    actual val accountRepository: AccountRepository get() = engine.accountRepository
    actual val categoryRepository: CategoryRepository get() = engine.categoryRepository
    actual val merchantRepository: MerchantRepository get() = engine.merchantRepository
    actual val tagRepository: TagRepository get() = engine.tagRepository
    actual val transactionRepository: TransactionRepository get() = engine.transactionRepository
    actual val personRepository: PersonRepository get() = engine.personRepository
    actual val lendBorrowRepository: LendBorrowRepository get() = engine.lendBorrowRepository
    actual val budgetRepository: BudgetRepository get() = engine.budgetRepository
    actual val attachmentRepository: AttachmentRepository get() = engine.attachmentRepository
    actual val insightRepository: InsightRepository get() = engine.insightRepository
    actual val captureInboxRepository: CaptureInboxRepository get() = engine.captureInboxRepository
    actual val agentQuestionRepository: app.hisaab.data.AgentQuestionRepository
        get() = engine.agentQuestionRepository
    actual val agentMemoryRepository: app.hisaab.data.AgentMemoryRepository
        get() = engine.agentMemoryRepository
    actual val captureConfigRepository: CaptureConfigRepository get() = engine.captureConfigRepository
    actual val captureCoordinator: CaptureCoordinator get() = engine.captureCoordinator

    actual val conversationRepository: ConversationRepository get() = engine.conversationRepository
    actual val audioRecorder: AudioRecorder get() = engine.audioRecorder
    actual val cloudTranscriber: CloudTranscriber get() = engine.cloudTranscriber
    actual fun agentRuntime(): AgentRuntime = engine.agentRuntime()

    actual val llmRouter: LlmRouter get() = engine.llmRouter
    actual val captureEvents: SharedFlow<CaptureEvent> get() = engine.captureEvents

    actual fun captureCoordinatorOrNull(): CaptureCoordinator? = engine.captureCoordinatorOrNull()
    actual fun startHistoryImport() = engine.startHistoryImport()
    actual fun kickCaptureAgent() = engine.kickCaptureAgent()
    actual val syncStatuses: StateFlow<app.hisaab.capture.SyncStatuses> get() = engine.syncStatuses

    actual fun lastCrashReport(): String? = CrashLog.read(context)
    actual fun clearCrashReport() = CrashLog.clear(context)

    actual suspend fun confirmCandidate(candidateId: String): Boolean = engine.confirmCandidate(candidateId)
    actual suspend fun confirmCandidateWithEdits(newTxn: NewTransaction, candidateId: String): String =
        engine.confirmCandidateWithEdits(newTxn, candidateId)

    actual fun startCapture() = engine.startCapture()
    actual fun stopCapture() = engine.restartCaptureAsync()
    actual fun masterSecretInMemory(): ByteArray? = engine.masterSecretInMemory()
    actual fun openDatabase(masterSecret: ByteArray): HisaabDatabase = engine.openDatabase(masterSecret)
    actual fun databaseOrNull(): HisaabDatabase? = engine.databaseOrNull()
    actual fun closeDatabase() = engine.closeDatabase()
    actual fun lockUi() = engine.lockUi()
}
