package app.hisaab.capture

import app.hisaab.domain.CaptureConfig
import app.hisaab.domain.EngineMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundPolicyTest {

    private fun cfg(sms: Boolean = false, email: Boolean = false, relay: Boolean = false, notifications: Boolean = false) =
        CaptureConfig(
            captureEnabled = sms, engineMode = EngineMode.CLOUD, onDeviceModel = "gemma", cloudProvider = null,
            cloudModel = null, redactionEnabled = true, alwaysReview = false, autoPostThreshold = 0.85,
            cloudConsentAt = null, retainRawBody = false, lastSmsCursor = 0L, relayCaptureEnabled = relay,
            emailCaptureEnabled = email, notificationCaptureEnabled = notifications, updatedAt = 0L,
        )

    @Test
    fun `no secret means no background capture`() {
        assertFalse(backgroundCaptureWanted(cfg(sms = true), hasMasterSecret = false)) // onboarding not finished
    }

    @Test
    fun `null config means no background capture`() {
        assertFalse(backgroundCaptureWanted(null, hasMasterSecret = true))
    }

    @Test
    fun `any one channel on is enough`() {
        assertTrue(backgroundCaptureWanted(cfg(sms = true), hasMasterSecret = true))
        assertTrue(backgroundCaptureWanted(cfg(email = true), hasMasterSecret = true))
        assertTrue(backgroundCaptureWanted(cfg(relay = true), hasMasterSecret = true))
        assertTrue(backgroundCaptureWanted(cfg(notifications = true), hasMasterSecret = true))
    }

    @Test
    fun `every channel off stops it`() {
        assertFalse(backgroundCaptureWanted(cfg(), hasMasterSecret = true))
    }
}
