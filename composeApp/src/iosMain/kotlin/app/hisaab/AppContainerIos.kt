package app.hisaab

import app.cash.sqldelight.db.SqlDriver
import app.hisaab.auth.AuthRepository
import app.hisaab.auth.SupabaseAuthRepository
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
import app.hisaab.capture.CaptureCoordinator
import app.hisaab.capture.CaptureCursorStore
import app.hisaab.capture.CaptureHandler
import app.hisaab.db.DatabaseDriverFactory
import app.hisaab.db.HisaabDatabase
import app.hisaab.platform.AppLifecycle
import app.hisaab.platform.BiometricAuth
import app.hisaab.platform.CaptureService
import app.hisaab.platform.ContactPicker
import app.hisaab.platform.ImagePicker
import app.hisaab.platform.PlatformFileStore
import app.hisaab.platform.SecureStorage

actual class AppContainer {
    actual val secureStorage: SecureStorage = SecureStorage()
    actual val biometricAuth: BiometricAuth = BiometricAuth()
    actual val contactPicker: ContactPicker = ContactPicker()
    actual val imagePicker: ImagePicker = ImagePicker()
    actual val fileStore: PlatformFileStore = PlatformFileStore()
    actual val lifecycle: AppLifecycle = AppLifecycle()
    actual val captureService: CaptureService = CaptureService()

    actual val cryptoService: CryptoService = CryptoService()
    actual val mnemonicService: MnemonicService = MnemonicService()
    actual val blobCrypto: BlobCrypto = BlobCrypto()

    actual val authRepository: AuthRepository = SupabaseAuthRepository()
    actual val databaseDriverFactory: DatabaseDriverFactory = DatabaseDriverFactory()

    private var cachedDriver: SqlDriver? = null
    private var cachedDatabase: HisaabDatabase? = null
    private var cachedMasterSecret: ByteArray? = null

    actual fun masterSecretInMemory(): ByteArray? = cachedMasterSecret?.copyOf()

    private fun requireDb(): HisaabDatabase = cachedDatabase
        ?: error("Database not open — onboarding incomplete")

    actual val accountRepository: AccountRepository
        get() = AccountRepository(requireDb())
    actual val categoryRepository: CategoryRepository
        get() = CategoryRepository(requireDb())
    actual val merchantRepository: MerchantRepository
        get() = MerchantRepository(requireDb())
    actual val tagRepository: TagRepository
        get() = TagRepository(requireDb())
    actual val transactionRepository: TransactionRepository
        get() = TransactionRepository(requireDb(), merchantRepository, tagRepository)
    actual val personRepository: PersonRepository
        get() = PersonRepository(requireDb())
    actual val lendBorrowRepository: LendBorrowRepository
        get() = LendBorrowRepository(requireDb(), transactionRepository)
    actual val budgetRepository: BudgetRepository
        get() = BudgetRepository(requireDb())
    actual val attachmentRepository: AttachmentRepository
        get() = AttachmentRepository(
            db = requireDb(),
            blobCrypto = blobCrypto,
            masterSecretProvider = ::masterSecretInMemory,
            fileStore = fileStore,
            cryptoService = cryptoService,
        )
    actual val insightRepository: InsightRepository
        get() = InsightRepository(requireDb())

    // M3-3: NoOpLlmRouter ships now; M3-4 replaces with DefaultLlmRouter.
    actual val llmRouter: app.hisaab.llm.LlmRouter = app.hisaab.llm.NoOpLlmRouter()

    // M3-3: backing flow the pipeline emits AutoPosted into; M3-5 collects captureEvents.
    private val captureEventsFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<app.hisaab.capture.CaptureEvent>(replay = 1, extraBufferCapacity = 15)
    actual val captureEvents: kotlinx.coroutines.flow.SharedFlow<app.hisaab.capture.CaptureEvent> =
        captureEventsFlow

    // M3-3: fresh CapturePipeline built on every access so it always binds the current open DB
    // (fresh-DB resolution pattern per lock/unlock lifecycle requirement).
    actual val capturePipeline: app.hisaab.capture.CapturePipeline
        get() = app.hisaab.capture.CapturePipeline(
            db = requireDb(),
            inboxRepo = captureInboxRepository,
            senderRepo = senderRepository,
            accountMatcher = app.hisaab.capture.AccountMatcher(accountRepository, senderRepository),
            preFilter = app.hisaab.capture.SmsPreFilter(senderRepository),
            llmRouter = llmRouter,
            txnRepo = transactionRepository,
            configRepo = captureConfigRepository,
            captureEvents = captureEventsFlow,
        )

    actual val captureInboxRepository: CaptureInboxRepository
        get() = CaptureInboxRepository(requireDb())
    actual val senderRepository: SenderRepository
        get() = SenderRepository(requireDb())
    actual val captureConfigRepository: CaptureConfigRepository
        get() = CaptureConfigRepository(requireDb())

    actual val captureCoordinator: CaptureCoordinator by lazy {
        val cursorStore = object : CaptureCursorStore {
            // resolve the repo FRESH each call so it tracks close/open DB cycles
            override suspend fun currentCursor(): Long = captureConfigRepository.get().lastSmsCursor
            override suspend fun advanceCursor(toMs: Long) { captureConfigRepository.setCursor(toMs) }
        }
        val handler = CaptureHandler { /* no-op until M3-3 pipeline lands */ }
        CaptureCoordinator(captureService, handler, cursorStore)
    }

    actual fun openDatabase(masterSecret: ByteArray): HisaabDatabase {
        cachedDatabase?.let { return it }
        cachedMasterSecret = masterSecret.copyOf()
        val keyCopy = cryptoService.deriveDbKey(masterSecret)
        val driver = databaseDriverFactory.createDriver(keyCopy)
        cachedDriver = driver
        val db = HisaabDatabase(driver)
        cachedDatabase = db
        // Idempotent auto-seed of default Cash account + 12 default categories.
        // runBlocking is acceptable here because callers (OnboardingViewModel.completeProfile,
        // LockScreen.attemptUnlock, RecoveryEntryScreen.attemptRestore) are already in a coroutine.
        kotlinx.coroutines.runBlocking {
            categoryRepository.ensureDefaults()
            accountRepository.ensureDefaultCashAccount()
        }
        return db
    }

    actual fun databaseOrNull(): HisaabDatabase? = cachedDatabase

    actual fun closeDatabase() {
        cachedDriver?.close()
        cachedDriver = null
        cachedDatabase = null
        cachedMasterSecret?.fill(0)
        cachedMasterSecret = null
    }
}
