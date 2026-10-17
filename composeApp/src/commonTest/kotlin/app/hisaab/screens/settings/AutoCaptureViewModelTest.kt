package app.hisaab.screens.settings

import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import app.hisaab.llm.ProviderId
import app.hisaab.screens.capture.FakeCaptureGateway
import app.hisaab.screens.capture.FakeLlmProvider
import app.hisaab.screens.capture.FakeLlmRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        onImport: () -> Unit = {},
        notificationPermission: suspend () -> Boolean = { true },
        memory: app.hisaab.data.AgentMemoryRepository = app.hisaab.data.AgentMemoryRepository(db),
        requestContacts: suspend () -> Boolean = { false },
        today: String = "2026-10-17",
    ): AutoCaptureViewModel {
        val config = CaptureConfigRepository(db, dispatcher)
        return AutoCaptureViewModel(
            configRepo = config,
            memories = memory.observeAll(),
            forgetMemoryById = memory::delete,
            hasContactsPermission = { false },
            requestContactsPermission = requestContacts,
            today = { today },
            hasSmsPermission = { gateway.hasSmsPermission() },
            requestSmsPermission = { gateway.requestSmsPermission() },
            startHistoryImport = onImport,
            requestNotificationPermission = notificationPermission,
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
    fun `setRedaction persists`() = runTest {
        val db = TestDatabase.create()
        val v = vm(db)
        v.setRedaction(false)
        advanceUntilIdle()
        assertEquals(false, CaptureConfigRepository(db, dispatcher).get().redactionEnabled)
    }

    @Test
    fun `forgetting a memory deletes it`() = runTest {
        val db = TestDatabase.create()
        val memory = app.hisaab.data.AgentMemoryRepository(db)
        db.transaction { memory.addBlocking("16216 is bKash") }
        val v = vm(db, memory = memory)
        val fact = memory.all().single()

        v.forgetMemory(fact.id)
        advanceUntilIdle()

        assertTrue(memory.all().isEmpty())
    }

    @Test
    fun `allowing contacts asks the system once and remembers the answer`() = runTest {
        var asked = 0
        val v = vm(requestContacts = { asked++; true })
        assertEquals(false, v.contactsAllowed.value)

        v.allowContacts()
        advanceUntilIdle()

        assertEquals(1, asked)
        assertEquals(true, v.contactsAllowed.value)
    }

    @Test
    fun `AI calls today counts only today's calls`() = runTest {
        val db = TestDatabase.create()
        val config = CaptureConfigRepository(db, dispatcher)
        config.recordAgentCall("2026-10-16")
        val v = vm(db, today = "2026-10-17")
        val collected = backgroundScope.launch(dispatcher) { v.aiCallsToday.collect {} }
        advanceUntilIdle()
        assertEquals(0, v.aiCallsToday.value)

        config.recordAgentCall("2026-10-17")
        config.recordAgentCall("2026-10-17")
        advanceUntilIdle()
        assertEquals(2, v.aiCallsToday.value)
        collected.cancel()
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
    fun `configureEmail strips app-password spaces and imports email history from the beginning`() = runTest {
        val keys = FakeKeyStore()
        val v = vm(keys = keys)
        v.configureEmail(" me@gmail.com ", "abcd efgh ijkl mnop") // Gmail shows app passwords in groups of 4
        advanceUntilIdle()
        assertEquals("me@gmail.com", keys.map["imap_email"])
        assertEquals("abcdefghijklmnop", keys.map["imap_password"])
        assertNull(keys.map["imap_email_cursor"]) // no "from now on" cursor: all-time history
    }

    @Test
    fun `turning on email capture also asks for notification permission`() = runTest {
        var asks = 0
        val v = vm(notificationPermission = { asks++; false })
        v.setEmailCaptureEnabled(true)
        advanceUntilIdle()
        assertEquals(1, asks)
        assertEquals(true, v.notificationsDenied.value)
    }

    @Test
    fun `connecting a mailbox asks for notification permission`() = runTest {
        var asks = 0
        val v = vm(notificationPermission = { asks++; true })
        v.configureEmail("me@gmail.com", "abcd efgh ijkl mnop")
        advanceUntilIdle()
        assertEquals(1, asks)
    }

    @Test
    fun `granting SMS capture also asks for notification permission`() = runTest {
        var asks = 0
        val v = vm(gateway = FakeCaptureGateway(permissionGranted = true), notificationPermission = { asks++; true })
        v.setCaptureEnabled(true)
        advanceUntilIdle()
        assertEquals(1, asks) // background sync shows its status in a notification
        assertEquals(false, v.notificationsDenied.value)
    }

    @Test
    fun `denied notification permission raises the warning but keeps capture on`() = runTest {
        val db = TestDatabase.create()
        val v = vm(db, FakeCaptureGateway(permissionGranted = true), notificationPermission = { false })
        v.setCaptureEnabled(true)
        advanceUntilIdle()
        assertEquals(true, v.notificationsDenied.value)
        assertTrue(CaptureConfigRepository(db, dispatcher).get().captureEnabled)
    }

    @Test
    fun `granting SMS permission starts the all-time history import`() = runTest {
        var imports = 0
        val v = vm(gateway = FakeCaptureGateway(permissionGranted = true), onImport = { imports++ })
        v.setCaptureEnabled(true)
        advanceUntilIdle()
        assertEquals(1, imports)
    }

    @Test
    fun `importAllHistory hands off to the app-scoped import`() = runTest {
        var imports = 0
        val v = vm(onImport = { imports++ })
        v.importAllHistory()
        assertEquals(1, imports)
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
        val v = AutoCaptureViewModel(
            configRepo = config,
            hasSmsPermission = { gateway.hasSmsPermission() },
            requestSmsPermission = { gateway.requestSmsPermission() },
            startHistoryImport = {},
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
            hasSmsPermission = { gateway.hasSmsPermission() },
            requestSmsPermission = { gateway.requestSmsPermission() },
            startHistoryImport = {},
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
            hasSmsPermission = { false },
            requestSmsPermission = { false },
            startHistoryImport = {},
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
