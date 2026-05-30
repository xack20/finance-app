package app.hisaab

import android.content.Context
import androidx.fragment.app.FragmentActivity
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
import app.hisaab.agent.AgentRuntime
import app.hisaab.agent.WriteBatchCommitter
import app.hisaab.agent.buildAgentToolRegistry
import app.hisaab.domain.CloudProvider
import app.hisaab.data.ConversationRepository
import app.hisaab.llm.AndroidLlmContext
import app.hisaab.llm.DefaultLlmRouter
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.cloud.ClaudeProvider
import app.hisaab.llm.cloud.GeminiProvider
import app.hisaab.llm.cloud.OpenAiProvider
import app.hisaab.platform.NoSpeechToText
import app.hisaab.platform.SpeechToText
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

actual class AppContainer(
    private val context: Context,
    activity: FragmentActivity,
) {
    init {
        AndroidLlmContext.appContext = context.applicationContext
    }

    actual val secureStorage: SecureStorage = SecureStorage(context)
    actual val biometricAuth: BiometricAuth = BiometricAuth(activity)
    actual val contactPicker: ContactPicker = ContactPicker(activity)
    actual val imagePicker: ImagePicker = ImagePicker(activity)
    actual val fileStore: PlatformFileStore = PlatformFileStore(context)
    actual val lifecycle: AppLifecycle = AppLifecycle()
    actual val captureService: CaptureService = CaptureService(context, activity)

    actual val cryptoService: CryptoService = CryptoService()
    actual val mnemonicService: MnemonicService = MnemonicService()
    actual val blobCrypto: BlobCrypto = BlobCrypto()

    // Real Supabase phone-OTP auth on all build types. Dev sign-in uses the Supabase project's
    // test-OTP (visible in the dashboard) — the former DEBUG-only BypassAuthRepository was removed.
    actual val authRepository: AuthRepository = SupabaseAuthRepository()
    actual val databaseDriverFactory: DatabaseDriverFactory = DatabaseDriverFactory(context)

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

    actual val speechToText: SpeechToText = NoSpeechToText

    actual fun agentRuntime(): AgentRuntime {
        val db = requireDb()
        val txns = TransactionRepository(db, merchantRepository, tagRepository)
        return AgentRuntime(
            registry = buildAgentToolRegistry(
                accountRepository, categoryRepository, merchantRepository,
                txns, insightRepository, personRepository,
            ),
            // Resolve the user's SELECTED cloud provider at call time. The agent has its own
            // consent (checked via isConsented), independent of the SMS-capture cloud consent —
            // so we map cloudProvider → adapter directly rather than via llmRouter.active().
            agentProvider = {
                val adapter: LlmProvider? = when (captureConfigRepository.get().cloudProvider) {
                    CloudProvider.CLAUDE -> claudeProvider
                    CloudProvider.GEMINI -> geminiProvider
                    CloudProvider.OPENAI -> openAiProvider
                    null -> null
                }
                if (adapter != null && adapter.isAvailable()) adapter as? AgentProvider else null
            },
            isConsented = { secureStorage.loadString("agent_consent_at") != null },
            accountNames = { accountRepository.observeActive().first().joinToString(", ") { it.name } },
            categoryNames = { categoryRepository.observeAll().first().joinToString(", ") { it.name } },
            committer = WriteBatchCommitter(
                db, accountRepository, categoryRepository, personRepository,
                txns, lendBorrowRepository, budgetRepository,
            ),
        )
    }

    // LLM HTTP client — stable singleton (not DB-scoped); cloud providers use OkHttp on Android.
    private val llmHttpClient: HttpClient = HttpClient(OkHttp)

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
    // On-device provider: cached singleton via AndroidLlmContext so MediaPipe engine
    // (~900MB) is allocated at most once per process life.
    private val onDeviceProvider = run {
        val ctx = context.applicationContext
        val manager = app.hisaab.llm.ModelManager(ctx)
        if (manager.isAnyModelAvailable()) {
            AndroidLlmContext.getOrCreateProvider(ctx) {
                captureConfigRepository.get().redactionEnabled
            }
        } else null
    }

    // M3-4: DefaultLlmRouter built fresh each access so it always binds the current
    // captureConfigRepository (fresh-DB accessor). Providers are singletons — no
    // reallocation per SMS.
    actual val llmRouter: LlmRouter
        get() = DefaultLlmRouter(
            configRepo = captureConfigRepository,
            secureStorage = secureStorage,
            onDeviceProvider = onDeviceProvider,
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
        CaptureCoordinator(captureService, handler, cursorStore) { raw, e ->
            // Per-item failures stay isolated (cursor doesn't advance), but log them so a
            // persistently-failing capture is visible instead of retried silently forever.
            android.util.Log.w("HisaabCapture", "Dropped capture item (ts=${raw.receivedAt}); will retry: ${e.message}")
        }
    }

    // M3-5: coordinator scope — created on DB open, cancelled on DB close.
    private var captureScope: kotlinx.coroutines.CoroutineScope? = null

    actual fun captureCoordinatorOrNull(): CaptureCoordinator? =
        if (cachedDatabase != null) captureCoordinator else null

    /**
     * Atomically posts a pending candidate to the ledger and marks it CONFIRMED in one db.transaction.
     * Links the txn at insert time via NewTransaction.captureId (R1: no link method).
     * Returns false if the candidate is missing required fields (account/amount/direction).
     */
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
            // addBlocking is the synchronous variant safe to use inside db.transaction {}
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
            // markConfirmed calls queries.updateStatus synchronously; safe inside db.transaction
            inbox.markConfirmedBlocking(candidateId)
        }
        return true
    }

    actual fun openDatabase(masterSecret: ByteArray): HisaabDatabase {
        cachedDatabase?.let { return it }
        cachedMasterSecret = masterSecret.copyOf()
        val keyCopy = cryptoService.deriveDbKey(masterSecret)
        val driver = databaseDriverFactory.createDriver(keyCopy)
        // DatabaseDriverFactory zeroes keyCopy after SQLCipher copies it (P0b T3 fix).
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
        // M3-int Fix 3: start capture only when captureEnabled is true (no unconditional start).
        startCapture()
        return db
    }

    actual fun databaseOrNull(): HisaabDatabase? = cachedDatabase

    actual fun startCapture() {
        if (captureScope != null) return
        if (cachedDatabase == null) return
        // Read captureEnabled synchronously; returns false if the singleton row doesn't exist yet.
        val enabled = kotlinx.coroutines.runBlocking { captureConfigRepository.get().captureEnabled }
        if (!enabled) return
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        )
        captureScope = scope
        captureCoordinator.start(scope)
        // M3-int Fix 2: wire catch-up so SMS received while DB was locked are recovered.
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
        // M3-5: drain in-flight process() coroutines before closing the driver to prevent
        // queries on a closed connection.
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
