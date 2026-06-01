package app.hisaab.platform

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.hisaab.ui.ScreenshotHostActivity
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Instrumented smoke test for [BiometricAuth].
 *
 * [BiometricAuth.authenticate] shows the system BiometricPrompt and resolves only through real
 * biometric UI interaction, so it cannot be driven by an automated test (it's verified manually on
 * device). [isAvailable] IS testable and is the capability gate the onboarding/lock flow reads: a
 * misconfigured authenticator constant or broken BiometricManager wiring would throw or misreport
 * here. BiometricPrompt/BiometricManager require a FragmentActivity, so we launch the real
 * [ScreenshotHostActivity] (a FragmentActivity) and assert the probe runs cleanly and deterministically.
 */
class BiometricAuthInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ScreenshotHostActivity>()

    @Test
    fun isAvailable_runs_without_crashing_and_is_deterministic() {
        val auth = BiometricAuth(composeRule.activity)

        val first = auth.isAvailable()
        // Same device state -> same answer; proves the probe is a pure, side-effect-free capability read
        // (and, with the call above, that BiometricManager + the BIOMETRIC_STRONG constant don't throw).
        assertEquals(first, auth.isAvailable())
    }
}
