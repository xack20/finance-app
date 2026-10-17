package app.hisaab.engine

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.hisaab.agent.AgentProvider
import app.hisaab.agent.AgentRuntime
import app.hisaab.agent.WriteBatchCommitter
import app.hisaab.agent.buildAgentToolRegistry
import app.hisaab.auth.AuthRepository
import app.hisaab.auth.SupabaseAuthRepository
import app.hisaab.capture.CaptureCoordinator
import app.hisaab.capture.CaptureCursorStore
import app.hisaab.capture.CaptureHandler
import app.hisaab.capture.ChannelStatusTracker
import app.hisaab.capture.SyncStatuses
import app.hisaab.capture.CompositeCaptureSource
import app.hisaab.capture.EmailCaptureSource
import app.hisaab.capture.ImapConfig
import app.hisaab.capture.ImapHosts
import app.hisaab.capture.RelayCaptureCrypto
import app.hisaab.capture.RelayCaptureSource
import app.hisaab.capture.SealedCapture
import app.hisaab.capture.SmsCaptureSource
import app.hisaab.capture.SupabaseRelayTransport
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
import app.hisaab.data.SenderRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.DatabaseDriverFactory
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.CloudProvider
import app.hisaab.llm.AndroidLlmContext
import app.hisaab.llm.DefaultLlmRouter
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.cloud.ClaudeProvider
import app.hisaab.llm.cloud.GeminiProvider
import app.hisaab.llm.cloud.GeminiTranscriber
import app.hisaab.llm.cloud.OpenAiProvider
import app.hisaab.platform.AndroidAudioRecorder
import app.hisaab.platform.AudioRecorder
import app.hisaab.platform.CloudTranscriber
import app.hisaab.platform.PlatformFileStore
import app.hisaab.platform.SecureStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-scoped owner of the encrypted DB and the whole capture engine (repositories, LLM providers, capture
 * sources, coordinator). Holds no Activity, so the UI's [app.hisaab.AppContainer] and background work share
 * ONE DB driver — UI flows see background writes live — and capture can outlive any screen.
 *
 * Moved verbatim from AppContainerAndroid (2026-10-17, background-capture plan Task 3); behavior unchanged.
 */
class HisaabEngine private constructor(
    private val context: Context,
    authRepository: AuthRepository,
) {
    init {
        AndroidLlmContext.appContext = context.applicationContext
    }

    val secureStorage: SecureStorage = SecureStorage(context)
    val fileStore: PlatformFileStore = PlatformFileStore(context)

    val cryptoService: CryptoService = CryptoService()
    val mnemonicService: MnemonicService = MnemonicService()
    val blobCrypto: BlobCrypto = BlobCrypto()

    // Real Supabase phone-OTP auth by default (dev sign-in uses the Supabase project's dashboard
    // test-OTP; the former DEBUG-only BypassAuthRepository was removed). Injectable via
    // AppContainer's optional constructor param so instrumented E2E tests can drive onboarding with a fake OTP.
    val authRepository: AuthRepository = authRepository
    val databaseDriverFactory: DatabaseDriverFactory = DatabaseDriverFactory(context)

    private var cachedDriver: SqlDriver? = null
    private var cachedDatabase: HisaabDatabase? = null
    private var cachedMasterSecret: ByteArray? = null

    fun masterSecretInMemory(): ByteArray? = cachedMasterSecret?.copyOf()

    private fun requireDb(): HisaabDatabase = cachedDatabase
        ?: error("Database not open — onboarding incomplete")

    val accountRepository: AccountRepository
        get() = AccountRepository(requireDb())
    val categoryRepository: CategoryRepository
        get() = CategoryRepository(requireDb())
    val merchantRepository: MerchantRepository
        get() = MerchantRepository(requireDb())
    val tagRepository: TagRepository
        get() = TagRepository(requireDb())
    val transactionRepository: TransactionRepository
        get() = TransactionRepository(requireDb(), merchantRepository, tagRepository)
    val personRepository: PersonRepository
        get() = PersonRepository(requireDb())
    val lendBorrowRepository: LendBorrowRepository
        get() = LendBorrowRepository(requireDb(), transactionRepository)
    val budgetRepository: BudgetRepository
        get() = BudgetRepository(requireDb())
    val attachmentRepository: AttachmentRepository
        get() = AttachmentRepository(
            db = requireDb(),
            blobCrypto = blobCrypto,
            masterSecretProvider = ::masterSecretInMemory,
            fileStore = fileStore,
            cryptoService = cryptoService,
        )
    val insightRepository: InsightRepository
        get() = InsightRepository(requireDb())

    // M4-6: agent DI members.
    val conversationRepository: ConversationRepository
        get() = ConversationRepository(requireDb())

    val audioRecorder: AudioRecorder = AndroidAudioRecorder(context)

    fun agentRuntime(): AgentRuntime {
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
            unavailableReason = {
                if (captureConfigRepository.get().cloudProvider == null)
                    "Pick a cloud model in Settings to use the assistant."
                else
                    "Add your cloud model's API key in Settings to use the assistant."
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
    // Cloud STT shares the Gemini key + HTTP client — used for Bangla voice (on-device STT can't).
    val cloudTranscriber: CloudTranscriber = GeminiTranscriber(
        httpClient = llmHttpClient,
        apiKey = { secureStorage.loadString("llm_api_key_GEMINI") },
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
    val llmRouter: LlmRouter
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
    val captureEvents: kotlinx.coroutines.flow.SharedFlow<app.hisaab.capture.CaptureEvent> =
        captureEventsFlow

    // M3-3: fresh CapturePipeline built on every access so it always binds the current open DB
    // (fresh-DB resolution pattern per lock/unlock lifecycle requirement).
    val capturePipeline: app.hisaab.capture.CapturePipeline
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

    val captureInboxRepository: CaptureInboxRepository
        get() = CaptureInboxRepository(requireDb())
    val senderRepository: SenderRepository
        get() = SenderRepository(requireDb())
    val captureConfigRepository: CaptureConfigRepository
        get() = CaptureConfigRepository(requireDb())

    /** SMS reading (inbox backfill + live bus) — no Activity; the permission request stays on CaptureService. */
    private val smsCaptureSource = SmsCaptureSource(context)

    // Per-channel sync status (Auto-capture screen + background notification). Engine-lifetime scope: the
    // combined flow outlives any one capture run.
    private val engineScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )
    private val clock: () -> Long = { System.currentTimeMillis() }
    private val smsStatus = ChannelStatusTracker(clock)
    private val emailStatus = ChannelStatusTracker(clock)
    private val relayStatus = ChannelStatusTracker(clock)

    val syncStatuses: kotlinx.coroutines.flow.StateFlow<SyncStatuses> =
        kotlinx.coroutines.flow.combine(smsStatus.status, emailStatus.status, relayStatus.status, ::SyncStatuses)
            .stateIn(engineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, SyncStatuses())

    /** SMS status outside an import: off, missing permission, or up to date as of now. */
    private suspend fun refreshSmsStatus() {
        when {
            !captureConfigRepository.get().captureEnabled -> smsStatus.off()
            !smsCaptureSource.hasSmsPermission() ->
                smsStatus.failed("SMS permission is off — tap Grant", persistent = true)
            else -> smsStatus.succeeded()
        }
    }

    val captureCoordinator: CaptureCoordinator by lazy {
        val cursorStore = object : CaptureCursorStore {
            // resolve the repo FRESH each call so it tracks close/open DB cycles
            override suspend fun currentCursor(): Long = captureConfigRepository.get().lastSmsCursor
            override suspend fun advanceCursor(toMs: Long) { captureConfigRepository.setCursor(toMs) }
        }
        // M3-3: reference capturePipeline inside the lambda so it resolves FRESH on each invocation
        // (ensures the pipeline always binds the current open DB after a lock/unlock cycle).
        val handler = CaptureHandler { capturePipeline.process(it) }
        // P2: the platform SMS/notification source PLUS the Mac→phone relay, fanned through the one
        // coordinator (single Mutex). The relay stays inert until relayCaptureEnabled + a master
        // secret + a session exist, so this composite is safe to always install.
        val source = CompositeCaptureSource(listOf(smsCaptureSource, relayCaptureSource, emailCaptureSource))
        CaptureCoordinator(source, handler, cursorStore, importState = importState) { raw, e ->
            // Per-item failures stay isolated (cursor doesn't advance), but log them so a
            // persistently-failing capture is visible instead of retried silently forever.
            android.util.Log.w("HisaabCapture", "Dropped capture item (ts=${raw.receivedAt}); will retry: ${e.message}")
        }
    }

    // Survives process death so an interrupted all-history import resumes on the next background start.
    private val importState = object : app.hisaab.capture.ImportStateStore {
        override suspend fun isImportPending() = secureStorage.loadString(IMPORT_PENDING_KEY) == "1"
        override suspend fun setImportPending(pending: Boolean) =
            secureStorage.storeString(IMPORT_PENDING_KEY, if (pending) "1" else "")
    }

    // P2: Mac→phone capture relay source. Inert (no fetch) until enabled() is true; decode derives
    // the relay key from the on-device master secret and opens each row (errors isolated upstream).
    private val relayCrypto = RelayCaptureCrypto()
    private val relayCaptureSource: RelayCaptureSource by lazy {
        RelayCaptureSource(
            transport = SupabaseRelayTransport(),
            decode = { row ->
                secureStorage.loadMasterSecret()?.let { master ->
                    relayCrypto.open(SealedCapture(row.nonce, row.ciphertext), relayCrypto.relayKey(master))
                }
            },
            enabled = {
                captureConfigRepository.get().relayCaptureEnabled &&
                    secureStorage.loadMasterSecret() != null &&
                    authRepository.isSignedIn()
            },
            onError = { e -> android.util.Log.w("HisaabRelay", "relay ingest: ${e.message}") },
            status = relayStatus,
        )
    }

    // EP3: in-app IMAP email capture. Inert until emailCaptureEnabled + saved credentials exist.
    private val emailCaptureSource: EmailCaptureSource by lazy {
        EmailCaptureSource(
            credentials = {
                val email = secureStorage.loadString("imap_email")
                val pw = secureStorage.loadString("imap_password")
                if (email.isNullOrBlank() || pw.isNullOrBlank()) {
                    null
                } else {
                    ImapHosts.forEmail(email)?.let { (host, port) -> ImapConfig(email, pw, host, port) }
                }
            },
            enabled = { captureConfigRepository.get().emailCaptureEnabled },
            // UID-based cursor (ImapUidCursor) under a new key: the old "imap_email_cursor" held a timestamp.
            loadCursor = { secureStorage.loadString("imap_email_uid_cursor")?.toLongOrNull() ?: 0L },
            saveCursor = { secureStorage.storeString("imap_email_uid_cursor", it.toString()) },
            onError = { e -> android.util.Log.w("HisaabEmail", "email capture: ${e.message}") },
            status = emailStatus,
        )
    }

    // M3-5: coordinator scope — created on DB open, cancelled on DB close.
    private var captureScope: kotlinx.coroutines.CoroutineScope? = null

    fun captureCoordinatorOrNull(): CaptureCoordinator? =
        if (cachedDatabase != null) captureCoordinator else null

    fun startHistoryImport() {
        captureScope?.launch(kotlinx.coroutines.Dispatchers.Default) { captureCoordinator.importAllHistory() }
    }

    /**
     * Atomically posts a pending candidate to the ledger and marks it CONFIRMED in one db.transaction.
     * Links the txn at insert time via NewTransaction.captureId (R1: no link method).
     * Returns false if the candidate is missing required fields (account/amount/direction).
     */
    suspend fun confirmCandidate(candidateId: String): Boolean {
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

    @Synchronized // the service (background thread) and the UI (main) both drive the DB lifecycle
    fun openDatabase(masterSecret: ByteArray): HisaabDatabase {
        cachedDatabase?.let { open ->
            // Still open because background capture kept it across the UI lock: just restore the UI's copy.
            cachedMasterSecret?.fill(0)
            cachedMasterSecret = masterSecret.copyOf()
            return open
        }
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

    fun databaseOrNull(): HisaabDatabase? = cachedDatabase

    @Synchronized
    fun startCapture() {
        if (captureScope != null) return
        if (cachedDatabase == null) return
        // Start if EITHER native capture (SMS/notification) or the Mac relay is on. Returns false if
        // the singleton config row doesn't exist yet.
        val enabled = kotlinx.coroutines.runBlocking {
            val cfg = captureConfigRepository.get()
            // Arm the OS-bound notification listener from the persisted flag — it can't read the
            // encrypted config itself (see NotificationCaptureGate). Runtime toggles set it directly.
            app.hisaab.capture.NotificationCaptureGate.enabled = cfg.notificationCaptureEnabled
            cfg.captureEnabled || cfg.relayCaptureEnabled || cfg.emailCaptureEnabled || cfg.notificationCaptureEnabled
        }
        if (!enabled) return
        CaptureServiceStarter.start(context) // keeps capture running once the app is minimized or locked
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        )
        captureScope = scope
        captureCoordinator.start(scope)
        // M3-int Fix 2: wire catch-up so SMS received while DB was locked are recovered.
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            captureCoordinator.catchUp()
            refreshSmsStatus()
        }
        // SMS import progress → status + a log line every 100 messages (adb-verifiable without the UI).
        scope.launch {
            var importing = false
            captureCoordinator.importProgress.collect { p ->
                if (p != null) {
                    importing = true
                    smsStatus.importing(p.done, p.total)
                    if (p.done % 100 == 0) android.util.Log.i("HisaabCapture", "sms import ${p.done}/${p.total}")
                } else {
                    if (importing) android.util.Log.i("HisaabCapture", "sms import finished")
                    importing = false
                    refreshSmsStatus()
                }
            }
        }
    }

    @Synchronized
    fun stopCapture() {
        val scope = captureScope ?: return
        captureScope = null
        kotlinx.coroutines.runBlocking {
            (scope.coroutineContext[kotlinx.coroutines.Job])?.let { j -> j.cancel(); j.join() }
        }
    }

    @Synchronized
    fun closeDatabase() {
        // M3-5: drain in-flight process() coroutines before closing the driver to prevent
        // queries on a closed connection.
        stopBackground()
        stopCapture()
        cachedDriver?.close()
        cachedDriver = null
        cachedDatabase = null
        cachedMasterSecret?.fill(0)
        cachedMasterSecret = null
    }

    suspend fun confirmCandidateWithEdits(
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

    private val _backgroundActive = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** True while the foreground service is running capture; the service stops itself when this turns false. */
    val backgroundActive: kotlinx.coroutines.flow.StateFlow<Boolean> = _backgroundActive
    private var backgroundJob: kotlinx.coroutines.Job? = null

    /**
     * Called by [CaptureForegroundService]: opens the DB with the stored master secret (no fingerprint needed —
     * the key was never auth-bound; the UI lock still guards viewing), starts capture, resumes an interrupted
     * import and re-runs SMS catch-up every 15 min. Idempotent. False = nothing to do (service stops).
     */
    suspend fun startBackground(): Boolean {
        val secret = secureStorage.loadMasterSecret() ?: return false
        try {
            if (cachedDatabase == null) openDatabase(secret)
        } finally {
            secret.fill(0)
        }
        if (!app.hisaab.capture.backgroundCaptureWanted(captureConfigRepository.get(), hasMasterSecret = true)) {
            return false
        }
        startCapture()
        if (_backgroundActive.value) return true
        _backgroundActive.value = true
        backgroundJob = engineScope.launch {
            captureCoordinator.resumePendingImport()
            while (true) {
                kotlinx.coroutines.delay(CATCH_UP_INTERVAL_MS)
                captureCoordinator.catchUp()
            }
        }
        return true
    }

    fun stopBackground() {
        backgroundJob?.cancel()
        backgroundJob = null
        _backgroundActive.value = false
    }

    /** UI lock: keep the DB open while background capture runs (only drop the UI's secret copy); else close it. */
    @Synchronized
    fun lockUi() {
        if (_backgroundActive.value) {
            cachedMasterSecret?.fill(0)
            cachedMasterSecret = null
        } else {
            closeDatabase()
        }
    }

    /**
     * Re-applies the capture toggles: stops capture, restarts it if any channel is still on, and ends background
     * work when none is. (Previously turning SMS off stopped email/relay capture too.)
     */
    @Synchronized
    fun restartCapture() {
        stopCapture()
        startCapture()
        if (captureScope == null) stopBackground()
    }

    companion object {
        private const val IMPORT_PENDING_KEY = "sms_history_import_pending"
        private const val CATCH_UP_INTERVAL_MS = 15L * 60 * 1000

        @Volatile private var instance: HisaabEngine? = null

        /** The process's engine. [authRepository] only applies on the first call (tests inject a fake there). */
        fun get(context: Context, authRepository: AuthRepository? = null): HisaabEngine =
            instance ?: synchronized(this) {
                instance ?: HisaabEngine(context.applicationContext, authRepository ?: SupabaseAuthRepository())
                    .also { instance = it }
            }

        /** Debug test host only: drop the engine (closing its DB) so each test launch gets a fresh one. */
        @androidx.annotation.VisibleForTesting
        fun resetForTests() = synchronized(this) {
            instance?.closeDatabase()
            instance = null
        }
    }
}
