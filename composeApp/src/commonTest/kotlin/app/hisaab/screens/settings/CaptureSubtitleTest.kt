package app.hisaab.screens.settings

import app.hisaab.domain.CaptureConfig
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureSubtitleTest {

    private fun cfg(
        engine: EngineMode = EngineMode.ON_DEVICE,
        provider: CloudProvider? = null,
        sms: Boolean = true,
        email: Boolean = false,
    ) = CaptureConfig(
        captureEnabled = sms, engineMode = engine, onDeviceModel = "gemma", cloudProvider = provider,
        cloudModel = null, redactionEnabled = true, alwaysReview = false, autoPostThreshold = 0.85,
        cloudConsentAt = null, retainRawBody = false, lastSmsCursor = 0L, emailCaptureEnabled = email, updatedAt = 0L,
    )

    @Test
    fun `cloud engine names the chosen provider`() {
        assertEquals("Cloud · Claude", captureSubtitle(cfg(EngineMode.CLOUD, CloudProvider.CLAUDE)))
    }

    @Test
    fun `cloud engine without a provider says so`() {
        assertEquals("Cloud · no provider chosen", captureSubtitle(cfg(EngineMode.CLOUD)))
    }

    @Test
    fun `on-device engine keeps the private label`() {
        assertEquals("On-device · private", captureSubtitle(cfg()))
    }

    @Test
    fun `every channel off reads Off whatever the engine`() {
        assertEquals("Off", captureSubtitle(cfg(EngineMode.CLOUD, CloudProvider.CLAUDE, sms = false)))
    }

    @Test
    fun `email-only capture still counts as on`() {
        assertEquals("Cloud · Claude", captureSubtitle(cfg(EngineMode.CLOUD, CloudProvider.CLAUDE, sms = false, email = true)))
    }

    @Test
    fun `nothing loaded yet shows nothing`() {
        assertEquals("", captureSubtitle(null))
    }
}
