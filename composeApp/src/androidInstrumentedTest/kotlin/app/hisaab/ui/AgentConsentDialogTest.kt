package app.hisaab.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.screens.agent.AgentConsentDialog
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Instrumented UI tests for AgentConsentDialog.
 * NOTE: Unrunnable on API 37 emulator (pre-existing project constraint); structurally correct.
 */
class AgentConsentDialogTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    // 1. Dialog renders — the consent dialog is displayed with the expected disclosure text.
    @Test
    fun dialog_showsDisclosureText() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                AgentConsentDialog(
                    onConsent = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("agent_consent_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Turn on the assistant").assertIsDisplayed()
        composeRule.onNodeWithText("What leaves your device:").assertIsDisplayed()
        composeRule.onNodeWithText("What never leaves your device:").assertIsDisplayed()
    }

    // 2. Confirm fires onConsent — tapping "Turn on assistant" invokes the onConsent callback.
    @Test
    fun confirmButton_firesOnConsent() {
        var consentFired = false
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                AgentConsentDialog(
                    onConsent = { consentFired = true },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("agent_consent_confirm").performClick()
        assertTrue(consentFired, "Expected onConsent to fire after tapping confirm")
    }

    // 3. Dismiss fires onDismiss — tapping "Not now" invokes the onDismiss callback.
    @Test
    fun dismissButton_firesOnDismiss() {
        var dismissFired = false
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                AgentConsentDialog(
                    onConsent = {},
                    onDismiss = { dismissFired = true },
                )
            }
        }
        composeRule.onNodeWithText("Not now").performClick()
        assertTrue(dismissFired, "Expected onDismiss to fire after tapping Not now")
    }
}
