package app.hisaab

import androidx.compose.runtime.staticCompositionLocalOf
import app.hisaab.auth.AuthRepository
import app.hisaab.crypto.BlobCrypto
import app.hisaab.crypto.CryptoService
import app.hisaab.crypto.MnemonicService
import app.hisaab.data.AccountRepository
import app.hisaab.data.AttachmentRepository
import app.hisaab.data.BudgetRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.InsightRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.DatabaseDriverFactory
import app.hisaab.db.HisaabDatabase
import app.hisaab.capture.CaptureCoordinator
import app.hisaab.platform.AppLifecycle
import app.hisaab.platform.BiometricAuth
import app.hisaab.platform.CaptureService
import app.hisaab.platform.ContactPicker
import app.hisaab.platform.ImagePicker
import app.hisaab.platform.PlatformFileStore
import app.hisaab.platform.SecureStorage

/**
 * Hand-wired DI root. One instance per Application lifetime.
 *
 * P0c-1 wiring: cryptography, secure storage, biometric, DB factory,
 * Supabase auth, lifecycle observer. P0c-2 Task 16 adds repository instances.
 *
 * `database` is null until `openDatabase(masterSecret)` succeeds.
 * Subsequent calls re-use the cached driver until `closeDatabase()` is called
 * (on background lock or sign-out).
 */
expect class AppContainer {
    val secureStorage: SecureStorage
    val biometricAuth: BiometricAuth
    val contactPicker: ContactPicker
    val imagePicker: ImagePicker
    val fileStore: PlatformFileStore
    val lifecycle: AppLifecycle

    val cryptoService: CryptoService
    val mnemonicService: MnemonicService
    val blobCrypto: BlobCrypto

    val authRepository: AuthRepository
    val databaseDriverFactory: DatabaseDriverFactory

    val accountRepository: AccountRepository
    val categoryRepository: CategoryRepository
    val merchantRepository: MerchantRepository
    val tagRepository: TagRepository
    val transactionRepository: TransactionRepository
    val personRepository: PersonRepository
    val lendBorrowRepository: LendBorrowRepository
    val budgetRepository: BudgetRepository
    val attachmentRepository: AttachmentRepository
    val insightRepository: InsightRepository
    val captureInboxRepository: CaptureInboxRepository
    val senderRepository: SenderRepository
    val captureConfigRepository: CaptureConfigRepository
    val captureService: CaptureService
    val captureCoordinator: CaptureCoordinator

    // M3-3: tiered parsing pipeline + LLM router contract + auto-post event stream.
    val llmRouter: app.hisaab.llm.LlmRouter
    val capturePipeline: app.hisaab.capture.CapturePipeline
    val captureEvents: kotlinx.coroutines.flow.SharedFlow<app.hisaab.capture.CaptureEvent>

    /** Null until the DB is open; started by openDatabase, cancelled by closeDatabase. */
    fun captureCoordinatorOrNull(): CaptureCoordinator?

    /**
     * Posts a pending candidate to the ledger and marks it CONFIRMED in ONE db transaction
     * (mirrors the auto-post atomicity). Returns false (and leaves the candidate PENDING) if it
     * can't be posted yet — e.g. no resolved account/amount/direction — so the user can Edit.
     * The txn is linked at insert time via NewTransaction.captureId; there is no separate link call.
     */
    suspend fun confirmCandidate(candidateId: String): Boolean

    /** Master secret cached in memory while DB is open. null when locked or pre-onboarding. */
    fun masterSecretInMemory(): ByteArray?

    /** Opens (or re-opens) the encrypted DB with the given key. Idempotent. */
    fun openDatabase(masterSecret: ByteArray): HisaabDatabase

    /** Returns the currently-open DB, or null if locked / pre-onboarding. */
    fun databaseOrNull(): HisaabDatabase?

    /** Closes the driver and zeroes the in-memory master_secret. */
    fun closeDatabase()
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap App() in CompositionLocalProvider(LocalAppContainer provides ...)")
}
