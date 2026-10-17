package app.hisaab.screens.settings

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import app.hisaab.domain.SenderMapping
import app.hisaab.llm.ProviderId
import app.hisaab.screens.capture.FakeCaptureGateway
import app.hisaab.screens.capture.FakeLlmProvider
import app.hisaab.screens.capture.FakeLlmRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AutoCaptureViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private class FakeKeyStore {
        val map = mutableMapOf<String, String>()
        fun load(k: String): String? = map[k]
        fun store(k: String, v: String) { map[k] = v }
        fun clear(k: String) { map.remove(k) }
    }

    private fun vm(
        db: app.hisaab.db.HisaabDatabase = TestDatabase.create(),
        gateway: FakeCaptureGateway = FakeCaptureGateway(),
        router: FakeLlmRouter = FakeLlmRouter(),
        keys: FakeKeyStore = FakeKeyStore(),
    ): AutoCaptureViewModel {
        val config = CaptureConfigRepository(db, dispatcher)
        val senders = SenderRepository(db)
        val accounts = AccountRepository(db)
        return AutoCaptureViewModel(
            configRepo = config,
            senderRepo = senders,
            accountRepo = accounts,
            hasSmsPermission = { gateway.hasSmsPermission() },
            requestSmsPermission = { gateway.requestSmsPermission() },
            // M3-int Fix 4: runBackfill receives nowMs; test shim derives the 90-day cursor so
            // the `backfill calls gateway with the configured cursor` assertion still passes.
            runBackfill = { nowMs ->
                val cursor = (nowMs - 90L * 24L * 60L * 60L * 1000L).coerceAtLeast(0L)
                gateway.backfillSince(cursor); Unit
            },
            loadApiKey = keys::load,
            storeApiKey = keys::store,
            clearApiKey = keys::clear,
            router = router,
        )
    }

    @Test
    fun `enabling capture requests permission and sets captureEnabled`() = runTest {
        val db = TestDatabase.create()
        val gateway = FakeCaptureGateway(permissionGranted = false)
        val v = vm(db, gateway)
        v.setCaptureEnabled(true)
        advanceUntilIdle()
        assertTrue(gateway.requestCalled)
        assertTrue(CaptureConfigRepository(db, dispatcher).get().captureEnabled)
    }

    @Test
    fun `setEngineMode cloud persists`() = runTest {
        val db = TestDatabase.create()
        val v = vm(db)
        v.setEngineMode(EngineMode.CLOUD)
        advanceUntilIdle()
        assertEquals(EngineMode.CLOUD, CaptureConfigRepository(db, dispatcher).get().engineMode)
    }

    @Test
    fun `setCloudProvider persists provider and model`() = runTest {
        val db = TestDatabase.create()
        val v = vm(db)
        v.setCloudProvider(CloudProvider.CLAUDE, "claude-test")
        advanceUntilIdle()
        val cfg = CaptureConfigRepository(db, dispatcher).get()
        assertEquals(CloudProvider.CLAUDE, cfg.cloudProvider)
        assertEquals("claude-test", cfg.cloudModel)
    }

    @Test
    fun `savedKeys reflects stored keys and updates on save and clear`() = runTest {
        val keys = FakeKeyStore().apply { store("llm_api_key_GEMINI", "g-key") }
        val v = vm(keys = keys)
        assertEquals(setOf(CloudProvider.GEMINI), v.savedKeys.value)

        // The Validate button reads this — it must flip the moment a key is saved, not on screen reopen.
        v.setApiKey(CloudProvider.CLAUDE, "sk-ant-test")
        assertEquals(setOf(CloudProvider.GEMINI, CloudProvider.CLAUDE), v.savedKeys.value)

        v.clearApiKeyFor(CloudProvider.GEMINI)
        assertEquals(setOf(CloudProvider.CLAUDE), v.savedKeys.value)
    }

    @Test
    fun `setApiKey stores under provider-scoped key`() = runTest {
        val keys = FakeKeyStore()
        val v = vm(keys = keys)
        v.setApiKey(CloudProvider.CLAUDE, "sk-abc")
        advanceUntilIdle()
        assertEquals("sk-abc", keys.map["llm_api_key_CLAUDE"])
    }

    @Test
    fun `validateApiKey reports valid when the provider accepts the key`() = runTest {
        val router = FakeLlmRouter(provider = FakeLlmProvider(ProviderId.CLOUD_CLAUDE, available = true))
        val v = vm(router = router)
        v.recordConsent(1L)
        advanceUntilIdle()
        v.validateApiKey()
        advanceUntilIdle()
        assertEquals(KeyValidation.Valid, v.keyValidation.value)
    }

    @Test
    fun `validateApiKey asks for consent before checking the key`() = runTest {
        val router = FakeLlmRouter(provider = FakeLlmProvider(ProviderId.CLOUD_CLAUDE, available = true))
        val v = vm(router = router)
        v.validateApiKey()
        advanceUntilIdle()
        val state = v.keyValidation.value
        assertTrue(state is KeyValidation.Invalid && state.reason.contains("consent", ignoreCase = true), "$state")
    }

    @Test
    fun `validateApiKey asks for a key when none is saved`() = runTest {
        val v = vm(router = FakeLlmRouter(provider = null))
        v.recordConsent(1L)
        advanceUntilIdle()
        v.validateApiKey()
        advanceUntilIdle()
        val state = v.keyValidation.value
        assertTrue(state is KeyValidation.Invalid && state.reason.contains("key", ignoreCase = true), "$state")
    }

    @Test
    fun `validateApiKey shows the provider's own rejection reason`() = runTest {
        val rejection = app.hisaab.llm.LlmError.ProviderError(400, "This API key is not scoped to a workspace")
        val router = FakeLlmRouter(FakeLlmProvider(ProviderId.CLOUD_CLAUDE, available = true, verifyError = rejection))
        val v = vm(router = router)
        v.recordConsent(1L)
        advanceUntilIdle()
        v.validateApiKey()
        advanceUntilIdle()
        assertEquals(KeyValidation.Invalid("This API key is not scoped to a workspace"), v.keyValidation.value)
    }

    @Test
    fun `setRedaction setAlwaysReview setThreshold persist`() = runTest {
        val db = TestDatabase.create()
        val v = vm(db)
        v.setRedaction(false)
        v.setAlwaysReview(true)
        v.setAutoPostThreshold(0.7)
        advanceUntilIdle()
        val cfg = CaptureConfigRepository(db, dispatcher).get()
        assertEquals(false, cfg.redactionEnabled)
        assertEquals(true, cfg.alwaysReview)
        assertEquals(0.7, cfg.autoPostThreshold)
    }

    @Test
    fun `resetThresholdToDefault restores 0_85`() = runTest {
        val db = TestDatabase.create()
        val v = vm(db)
        v.setAutoPostThreshold(0.55)
        advanceUntilIdle()
        assertEquals(0.55, CaptureConfigRepository(db, dispatcher).get().autoPostThreshold)
        v.resetThresholdToDefault()
        advanceUntilIdle()
        assertEquals(DEFAULT_AUTO_POST_THRESHOLD, CaptureConfigRepository(db, dispatcher).get().autoPostThreshold)
        assertEquals(0.85, DEFAULT_AUTO_POST_THRESHOLD)
    }

    @Test
    fun `consent survives leaving the screen because navigation waits for the save`() = runTest {
        val db = TestDatabase.create()
        val v = vm(db)
        var navigated = false
        // Leaving the consent screen disposes its VM (cancelling its scope) — it must happen after the write.
        v.recordConsent(nowMs = 42L) { navigated = true; v.dispose() }
        advanceUntilIdle()
        assertTrue(navigated)
        assertEquals(42L, CaptureConfigRepository(db, dispatcher).get().cloudConsentAt)
    }

    @Test
    fun `recordConsent and revokeConsent toggle cloudConsentAt`() = runTest {
        val db = TestDatabase.create()
        val v = vm(db)
        v.recordConsent(nowMs = 123_456L)
        advanceUntilIdle()
        assertEquals(123_456L, CaptureConfigRepository(db, dispatcher).get().cloudConsentAt)
        v.revokeConsent()
        advanceUntilIdle()
        assertNull(CaptureConfigRepository(db, dispatcher).get().cloudConsentAt)
    }

    @Test
    fun `addSender and setSenderAccount persist`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val accountId = accounts.add("bKash", app.hisaab.domain.AccountKind.MFS, null)
        val v = vm(db)
        v.addSender(senderId = "MYBANK", displayName = "My Bank", bankType = BankType.BANK)
        advanceUntilIdle()
        v.setSenderAccount("MYBANK", accountId)
        advanceUntilIdle()
        val mapping = SenderRepository(db).findBySenderId("MYBANK")
        assertEquals("My Bank", mapping?.displayName)
        assertEquals(accountId, mapping?.accountId)
    }

    @Test
    fun `setSenderEnabled toggles isFinancial via upsert`() = runTest {
        val db = TestDatabase.create()
        val senders = SenderRepository(db)
        senders.upsert(
            SenderMapping(
                id = "s1", senderId = "PROMO", displayName = "Promo", bankType = BankType.OTHER,
                isFinancial = true, templateKey = null, accountId = null, createdAt = 1L,
            ),
        )
        val v = vm(db)
        v.setSenderEnabled("PROMO", false)
        advanceUntilIdle()
        assertEquals(false, SenderRepository(db).findBySenderId("PROMO")?.isFinancial)
    }

    @Test
    fun `unmappedSenders surfaces only financial senders without an account`() = runTest {
        val db = TestDatabase.create()
        val senders = SenderRepository(db)
        val accounts = AccountRepository(db)
        val mappedAccount = accounts.add("Mapped", app.hisaab.domain.AccountKind.MFS, null)
        // financial + unmapped → should surface as a "new sender detected" prompt
        senders.upsert(
            SenderMapping(
                id = "u1", senderId = "NEWBANK", displayName = "New Bank", bankType = BankType.BANK,
                isFinancial = true, templateKey = null, accountId = null, createdAt = 1L,
            ),
        )
        // financial + mapped → should NOT surface
        senders.upsert(
            SenderMapping(
                id = "m1", senderId = "OLDBANK", displayName = "Old Bank", bankType = BankType.BANK,
                isFinancial = true, templateKey = null, accountId = mappedAccount, createdAt = 1L,
            ),
        )
        // non-financial + unmapped → should NOT surface
        senders.upsert(
            SenderMapping(
                id = "p1", senderId = "PROMO", displayName = "Promo", bankType = BankType.OTHER,
                isFinancial = false, templateKey = null, accountId = null, createdAt = 1L,
            ),
        )
        val v = vm(db)
        advanceUntilIdle()
        val unmapped = v.unmappedSenders.first { it.isNotEmpty() }
        assertEquals(listOf("NEWBANK"), unmapped.map { it.senderId })
    }

    @Test
    fun `backfill calls gateway with the configured cursor`() = runTest {
        val db = TestDatabase.create()
        val gateway = FakeCaptureGateway()
        val v = vm(db, gateway)
        v.backfillLast90Days(nowMs = 90L * 24 * 60 * 60 * 1000)
        advanceUntilIdle()
        assertEquals(0L, gateway.lastBackfillCursor) // 90 days before "now=90d" == 0
    }

    // M3-int Fix 3: captureEnabled gates coordinator start/stop via onStartCapture/onStopCapture.
    @Test
    fun `setCaptureEnabled false calls onStopCapture and does not call onStartCapture`() = runTest {
        val db = TestDatabase.create()
        var startCount = 0
        var stopCount = 0
        val gateway = FakeCaptureGateway(permissionGranted = true)
        val config = CaptureConfigRepository(db, dispatcher)
        config.setCaptureEnabled(true) // already enabled
        val senders = SenderRepository(db)
        val accounts = AccountRepository(db)
        val v = AutoCaptureViewModel(
            configRepo = config,
            senderRepo = senders,
            accountRepo = accounts,
            hasSmsPermission = { gateway.hasSmsPermission() },
            requestSmsPermission = { gateway.requestSmsPermission() },
            runBackfill = { _ -> },
            loadApiKey = { null },
            storeApiKey = { _, _ -> },
            clearApiKey = { _ -> },
            router = FakeLlmRouter(),
            onStartCapture = { startCount++ },
            onStopCapture = { stopCount++ },
        )
        v.setCaptureEnabled(false)
        advanceUntilIdle()
        assertEquals(0, startCount, "onStartCapture must NOT be called when disabling")
        assertEquals(1, stopCount, "onStopCapture must be called when disabling")
    }

    @Test
    fun `setCaptureEnabled true calls onStartCapture`() = runTest {
        val db = TestDatabase.create()
        var startCount = 0
        var stopCount = 0
        val gateway = FakeCaptureGateway(permissionGranted = true)
        val v = AutoCaptureViewModel(
            configRepo = CaptureConfigRepository(db, dispatcher),
            senderRepo = SenderRepository(db),
            accountRepo = AccountRepository(db),
            hasSmsPermission = { gateway.hasSmsPermission() },
            requestSmsPermission = { gateway.requestSmsPermission() },
            runBackfill = { _ -> },
            loadApiKey = { null },
            storeApiKey = { _, _ -> },
            clearApiKey = { _ -> },
            router = FakeLlmRouter(),
            onStartCapture = { startCount++ },
            onStopCapture = { stopCount++ },
        )
        v.setCaptureEnabled(true)
        advanceUntilIdle()
        assertEquals(1, startCount, "onStartCapture must be called when enabling")
        assertEquals(0, stopCount, "onStopCapture must NOT be called when enabling")
    }

    @Test
    fun `setNotificationCaptureEnabled persists flag flips gate and starts only on enable`() = runTest {
        val db = TestDatabase.create()
        var gate: Boolean? = null
        var startCount = 0
        val v = AutoCaptureViewModel(
            configRepo = CaptureConfigRepository(db, dispatcher),
            senderRepo = SenderRepository(db),
            accountRepo = AccountRepository(db),
            hasSmsPermission = { false },
            requestSmsPermission = { false },
            runBackfill = { _ -> },
            loadApiKey = { null },
            storeApiKey = { _, _ -> },
            clearApiKey = { _ -> },
            router = FakeLlmRouter(),
            onStartCapture = { startCount++ },
            onSetNotificationGate = { gate = it },
        )
        v.setNotificationCaptureEnabled(true)
        advanceUntilIdle()
        assertEquals(true, CaptureConfigRepository(db, dispatcher).get().notificationCaptureEnabled)
        assertEquals(true, gate, "gate must arm when enabling")
        assertEquals(1, startCount, "onStartCapture must run when enabling")

        v.setNotificationCaptureEnabled(false)
        advanceUntilIdle()
        assertEquals(false, CaptureConfigRepository(db, dispatcher).get().notificationCaptureEnabled)
        assertEquals(false, gate, "gate must disarm when disabling")
        assertEquals(1, startCount, "disabling must NOT start capture again")
    }
}
