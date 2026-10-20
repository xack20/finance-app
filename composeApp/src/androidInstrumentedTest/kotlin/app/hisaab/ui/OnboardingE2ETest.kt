package app.hisaab.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import app.hisaab.App
import app.hisaab.AppContainer
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabTheme
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end onboarding flow against the REAL [App] root, with a [FakeAuthRepository] injected so
 * OTP send/verify succeed without a live Supabase phone number. Drives the full funnel
 * Welcome → OTP → Biometric(skip) → Recovery phrase → Profile, then asserts the app reaches the
 * authenticated shell and the encrypted DB was opened by the app itself.
 */
@OptIn(ExperimentalTestApi::class)
class OnboardingE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ScreenshotHostActivity>()

    private lateinit var container: AppContainer

    @Before
    fun freshDb() {
        // Start from a clean slate: App() opens the encrypted DB itself during completeProfile().
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("hisaab.db")
    }

    @After
    fun tearDown() {
        if (::container.isInitialized) container.closeDatabase()
    }

    @Test
    fun onboarding_full_funnel_reaches_authenticated_shell() {
        // Container was built in onCreate with the fake auth (set in @BeforeClass). Do NOT openDatabase —
        // onboarding does it.
        container = composeRule.activity.container

        composeRule.setContent {
            HisaabTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    App()
                }
            }
        }

        // Splash → Unauthenticated → OnboardingGraph(welcome). Wait for the Welcome screen.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("1X XXXX XXXX").performTextInput("1712345678")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue").performClick()

        // sendOtp (fake) → otp screen.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Verify").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("6-digit code").performTextInput("123456")
        composeRule.waitForIdle()
        // "Verify" matches both the eyebrow label and the button — target the clickable one.
        composeRule.onAllNodesWithText("Verify").filterToOne(hasClickAction()).performClick()

        // verifyOtp (fake) → biometric screen → Skip (generates + persists master_secret, no prompt).
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Skip (not recommended)").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Skip (not recommended)").performClick()

        // Recovery phrase → tick the (only) toggleable node, then save.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("I've saved them").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(isToggleable()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("I've saved them").performClick()

        // Profile → name + start. completeProfile opens the DB inline + inserts the profile + onComplete.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Start Hisaab →").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Name").performTextInput("Zakaria")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start Hisaab →").performClick()

        // Authenticated shell (MainGraph → TodayScreen).
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Today").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("No entries yet.\nTap + to record your first.").assertIsDisplayed()
        assertNotNull("DB should be open after onboarding completes", container.databaseOrNull())
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun injectFakeAuth() {
            ScreenshotHostActivity.authOverride = FakeAuthRepository(signedIn = false)
        }

        @JvmStatic
        @AfterClass
        fun clearFakeAuth() {
            ScreenshotHostActivity.authOverride = null
        }
    }
}
