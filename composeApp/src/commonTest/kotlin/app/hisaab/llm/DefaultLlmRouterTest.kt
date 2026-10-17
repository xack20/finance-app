package app.hisaab.llm

import app.hisaab.domain.CaptureConfig
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import app.hisaab.llm.support.FakeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultLlmRouterTest {

    private fun config(
        engineMode: EngineMode,
        cloudProvider: CloudProvider? = null,
        cloudConsentAt: Long? = null,
    ) = CaptureConfig(
        captureEnabled = true,
        engineMode = engineMode,
        onDeviceModel = "auto",
        cloudProvider = cloudProvider,
        cloudModel = null,
        redactionEnabled = true,
        alwaysReview = false,
        autoPostThreshold = 0.85,
        cloudConsentAt = cloudConsentAt,
        retainRawBody = true,
        lastSmsCursor = 0L,
        updatedAt = 0L,
    )

    private fun router(
        cfg: CaptureConfig,
        keys: Map<CloudProvider, String?> = emptyMap(),
        onDevice: LlmProvider? = null,
    ): DefaultLlmRouter = DefaultLlmRouter(
        configSource = { cfg },
        keyLoader = { provider -> keys[provider] },
        onDeviceProvider = onDevice,
        claude = FakeProvider(ProviderId.CLOUD_CLAUDE, available = keys[CloudProvider.CLAUDE] != null),
        gemini = FakeProvider(ProviderId.CLOUD_GEMINI, available = keys[CloudProvider.GEMINI] != null),
        openai = FakeProvider(ProviderId.CLOUD_OPENAI, available = keys[CloudProvider.OPENAI] != null),
    )

    @Test
    fun `on-device mode returns on-device provider when available`() = runTest {
        val od = FakeProvider(ProviderId.ON_DEVICE, available = true)
        val r = router(config(EngineMode.ON_DEVICE), onDevice = od)
        assertEquals(ProviderId.ON_DEVICE, r.active()?.id)
    }

    @Test
    fun `on-device mode returns null when model unavailable`() = runTest {
        val od = FakeProvider(ProviderId.ON_DEVICE, available = false)
        val r = router(config(EngineMode.ON_DEVICE), onDevice = od)
        assertNull(r.active())
    }

    @Test
    fun `on-device mode returns null when no provider on platform`() = runTest {
        val r = router(config(EngineMode.ON_DEVICE), onDevice = null)
        assertNull(r.active())
    }

    @Test
    fun `cloud mode returns selected provider when consented and key present`() = runTest {
        val r = router(
            config(EngineMode.CLOUD, CloudProvider.GEMINI, cloudConsentAt = 123L),
            keys = mapOf(CloudProvider.GEMINI to "g-key"),
        )
        assertEquals(ProviderId.CLOUD_GEMINI, r.active()?.id)
    }

    @Test
    fun `cloud mode returns null without consent`() = runTest {
        val r = router(
            config(EngineMode.CLOUD, CloudProvider.CLAUDE, cloudConsentAt = null),
            keys = mapOf(CloudProvider.CLAUDE to "k"),
        )
        assertNull(r.active())
    }

    @Test
    fun `cloud mode returns null when key missing`() = runTest {
        val r = router(
            config(EngineMode.CLOUD, CloudProvider.OPENAI, cloudConsentAt = 1L),
            keys = emptyMap(),
        )
        assertNull(r.active())
    }

    @Test
    fun `cloud mode returns null when no provider selected`() = runTest {
        val r = router(config(EngineMode.CLOUD, cloudProvider = null, cloudConsentAt = 1L))
        assertNull(r.active())
    }

    @Test
    fun `Claude via the Mac is used once its address is saved and consent given`() = runTest {
        val mac = FakeProvider(ProviderId.CLOUD_CLAUDE, available = true)
        val withAddress = DefaultLlmRouter(
            configSource = { config(EngineMode.CLOUD, CloudProvider.CLAUDE_MAC, cloudConsentAt = 1L) },
            keyLoader = { provider -> if (provider == CloudProvider.CLAUDE_MAC) "192.168.1.5" else null },
            onDeviceProvider = null,
            claude = FakeProvider(ProviderId.CLOUD_CLAUDE, available = true),
            gemini = FakeProvider(ProviderId.CLOUD_GEMINI, available = false),
            openai = FakeProvider(ProviderId.CLOUD_OPENAI, available = false),
            claudeMac = mac,
        )
        kotlin.test.assertSame(mac, withAddress.active())

        val noAddress = DefaultLlmRouter(
            configSource = { config(EngineMode.CLOUD, CloudProvider.CLAUDE_MAC, cloudConsentAt = 1L) },
            keyLoader = { null },
            onDeviceProvider = null,
            claude = FakeProvider(ProviderId.CLOUD_CLAUDE, available = true),
            gemini = FakeProvider(ProviderId.CLOUD_GEMINI, available = false),
            openai = FakeProvider(ProviderId.CLOUD_OPENAI, available = false),
            claudeMac = mac,
        )
        assertNull(noAddress.active()) // never falls back to the API-key Claude
    }
}

