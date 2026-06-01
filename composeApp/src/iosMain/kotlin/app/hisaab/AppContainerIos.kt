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
import app.hisaab.agent.AgentProvider
import app.hisaab.agent.NativeAgentRegistry
import app.hisaab.agent.OnDeviceAgentProvider
import app.hisaab.agent.AgentRuntime
import app.hisaab.agent.WriteBatchCommitter
import app.hisaab.agent.buildAgentToolRegistry
import app.hisaab.domain.CloudProvider
import app.hisaab.data.ConversationRepository
import app.hisaab.llm.DefaultLlmRouter
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.cloud.ClaudeProvider
import app.hisaab.llm.cloud.GeminiProvider
import app.hisaab.llm.cloud.OpenAiProvider
import app.hisaab.platform.IosSpeechToText
import app.hisaab.platform.SpeechToText
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    // M4-6: agent DI members.
    actual val conversationRepository: ConversationRepository
        get() = ConversationRepository(requireDb())

    actual val speechToText: SpeechToText = IosSpeechToText()

    /** The user's selected cloud provider, if one is picked AND its API key is present. */
    private suspend fun cloudAgentProvider(): AgentProvider? {
        val adapter: LlmProvider? = when (captureConfigRepository.get().cloudProvider) {
            CloudProvider.CLAUDE -> claudeProvider
            CloudProvider.GEMINI -> geminiProvider
            CloudProvider.OPENAI -> openAiProvider
            null -> null
        }
        return if (adapter != null && adapter.isAvailable()) adapter as? AgentProvider else null
    }

    /** Apple's on-device model, if the Swift FoundationModels bridge reports it available. */
    private fun onDeviceAgentProvider(): AgentProvider? =
        NativeAgentRegistry.bridge?.takeIf { it.isAvailable() }?.let { OnDeviceAgentProvider(it) }

    actual fun agentRuntime(): AgentRuntime {
        val db = requireDb()
        val txns = TransactionRepository(db, merchantRepository, tagRepository)
        return AgentRuntime(
            registry = buildAgentToolRegistry(
                accountRepository, categoryRepository, merchantRepository,
                txns, insightRepository, personRepository,
            ),
            // Resolve the user's SELECTED cloud provider at call time (agent consent is separate,
            // checked via isConsented — independent of the SMS-capture cloud consent).
            // A configured cloud provider (the user brought a key) takes priority — it's the more capable
            // model for the tool-calling agent. The on-device model (Apple Intelligence) is the private
            // default + fallback when no cloud key is set.
            agentProvider = { cloudAgentProvider() ?: onDeviceAgentProvider() },
            unavailableReason = {
                if (captureConfigRepository.get().cloudProvider == null)
                    "Add a cloud model + API key in Settings (Auto-capture → Engine → Cloud), or enable Apple Intelligence."
                else
                    "Add your cloud model's API key in Settings (Auto-capture → Engine → Cloud)."
            },
            // Cloud requires explicit consent; the on-device model does not (nothing leaves the device).
            isConsented = {
                if (cloudAgentProvider() != null) {
                    secureStorage.loadString("agent_consent_at") != null
                } else {
                    true
                }
            },
            accountNames = { accountRepository.observeActive().first().joinToString(", ") { it.name } },
            categoryNames = { categoryRepository.observeAll().first().joinToString(", ") { it.name } },
            committer = WriteBatchCommitter(
                db, accountRepository, categoryRepository, personRepository,
                txns, lendBorrowRepository, budgetRepository,
            ),
        )
    }

    // LLM HTTP client — stable singleton; Darwin engine on iOS.
    private val llmHttpClient: HttpClient = HttpClient(Darwin)

    // Singleton cloud providers: constructed once, apiKey and redact lambdas read live values
    // at call time so the providers survive lock/unlock cycles without holding DB references.
    private val claudeProvider: ClaudeProvider = ClaudeProvider(
        httpClient = llmHttpClient,
        apiKey = { secureStorage.loadString("llm_api_key_CLAUDE") },
        redact = { captureConfigRepository.get().redactionEnabled },
    )
    private val geminiProvider: GeminiProvider = GeminiProvider(
        httpClient = llmHttpClient,
        apiKey = { secureStorage.loadString("llm_api_key_GEMINI") },
        redact = { captureConfigRepository.get().redactionEnabled },
    )
    private val openAiProvider: OpenAiProvider = OpenAiProvider(
        httpClient = llmHttpClient,
        apiKey = { secureStorage.loadString("llm_api_key_OPENAI") },
        redact = { captureConfigRepository.get().redactionEnabled },
    )
    // iOS/wasm: createOnDeviceProvider() returns null; no on-device engine on these platforms.

    // M3-4: DefaultLlmRouter built fresh each access (fresh-DB pattern). Providers are
    // singletons — no reallocation per SMS.
    actual val llmRouter: LlmRouter
        get() = DefaultLlmRouter(
            configRepo = captureConfigRepository,
            secureStorage = secureStorage,
            onDeviceProvider = null,
            claude = claudeProvider,
            gemini = geminiProvider,
            openai = openAiProvider,
        )

    // M3-3: backing flow the pipeline emits AutoPosted into; M3-5 collects captureEvents.
    // replay=0: no stale-event replay when snackbar host subscribes (M3-5 guard).
    private val captureEventsFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<app.hisaab.capture.CaptureEvent>(extraBufferCapacity = 16)
    actual val captureEvents: kotlinx.coroutines.flow.SharedFlow<app.hisaab.capture.CaptureEvent> =
        captureEventsFlow

    // M3-3: fresh CapturePipeline built on every access so it always binds the current open DB
    // (fresh-DB resolution pattern per lock/unlock lifecycle requirement).
    actual val capturePipeline: app.hisaab.capture.CapturePipeline
        get() = app.hisaab.capture.CapturePipeline(
            db = requireDb(),
            inboxRepo = captureInboxRepository,
            senderRepo = senderRepository,
            accountMatcher = app.hisaab.capture.AccountMatcher(accountRepository, senderRepository, requireDb()),
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
        // M3-3: reference capturePipeline inside the lambda so it resolves FRESH on each invocation
        // (ensures the pipeline always binds the current open DB after a lock/unlock cycle).
        val handler = CaptureHandler { capturePipeline.process(it) }
        CaptureCoordinator(captureService, handler, cursorStore)
    }

    // M3-5: coordinator scope — created on DB open, cancelled on DB close.
    private var captureScope: kotlinx.coroutines.CoroutineScope? = null

    actual fun captureCoordinatorOrNull(): CaptureCoordinator? =
        if (cachedDatabase != null) captureCoordinator else null

    actual suspend fun confirmCandidate(candidateId: String): Boolean {
        val db = cachedDatabase ?: return false
        val inbox = CaptureInboxRepository(db)
        val txnRepo = transactionRepository
        val c = inbox.getById(candidateId) ?: return false
        val accountId = c.proposedAccountId ?: return false
        val amount = c.amount ?: return false
        val kind = when (c.direction) {
            app.hisaab.domain.Direction.DEBIT -> app.hisaab.domain.TxnKind.EXPENSE
            app.hisaab.domain.Direction.CREDIT -> app.hisaab.domain.TxnKind.INCOME
            null -> return false
        }
        db.transaction {
            txnRepo.addBlocking(
                app.hisaab.domain.NewTransaction(
                    accountId = accountId,
                    amount = amount,
                    currency = c.currency,
                    ts = c.receivedAt,
                    merchantName = c.proposedMerchant,
                    categoryId = c.proposedCategoryId,
                    source = app.hisaab.domain.TxnSource.SMS,
                    notes = null,
                    kind = kind,
                    captureId = candidateId,
                ),
            )
            inbox.markConfirmedBlocking(candidateId)
        }
        return true
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
            senderRepository.seedKnownSenders()
        }
        // M3-int Fix 3: start capture only when captureEnabled is true.
        startCapture()
        return db
    }

    actual fun databaseOrNull(): HisaabDatabase? = cachedDatabase

    actual fun startCapture() {
        if (captureScope != null) return
        if (cachedDatabase == null) return
        val enabled = kotlinx.coroutines.runBlocking { captureConfigRepository.get().captureEnabled }
        if (!enabled) return
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        )
        captureScope = scope
        captureCoordinator.start(scope)
        // M3-int Fix 2: recover SMS received while DB was locked (no-op on iOS — CaptureService
        // yields nothing, but the call is symmetric and harmless).
        scope.launch(kotlinx.coroutines.Dispatchers.Default) { captureCoordinator.catchUp() }
    }

    actual fun stopCapture() {
        val scope = captureScope ?: return
        captureScope = null
        kotlinx.coroutines.runBlocking {
            (scope.coroutineContext[kotlinx.coroutines.Job])?.let { j -> j.cancel(); j.join() }
        }
    }

    actual fun closeDatabase() {
        // Drain in-flight coroutines before closing the driver.
        stopCapture()
        cachedDriver?.close()
        cachedDriver = null
        cachedDatabase = null
        cachedMasterSecret?.fill(0)
        cachedMasterSecret = null
    }

    actual suspend fun confirmCandidateWithEdits(
        newTxn: app.hisaab.domain.NewTransaction,
        candidateId: String,
    ): String {
        val db = cachedDatabase ?: error("Database not open — cannot confirm candidate with edits")
        val inbox = CaptureInboxRepository(db)
        var txnId = ""
        db.transaction {
            txnId = transactionRepository.addBlocking(newTxn)
            inbox.markConfirmedBlocking(candidateId)
        }
        return txnId
    }
}
