package app.hisaab.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.hisaab.agent.AgentAvailability
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.AgentMessage
import app.hisaab.domain.AgentRole
import app.hisaab.screens.agent.AgentScreenContent
import app.hisaab.screens.agent.AgentUiState
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AgentScreenTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private fun userMessage(id: String, content: String) = AgentMessage(
        id = id,
        conversationId = "conv-test",
        role = AgentRole.USER,
        content = content,
        proposedWrites = emptyList(),
        appliedSummary = null,
        createdAt = 1_000L,
    )

    private fun assistantMessage(id: String, content: String) = AgentMessage(
        id = id,
        conversationId = "conv-test",
        role = AgentRole.ASSISTANT,
        content = content,
        proposedWrites = emptyList(),
        appliedSummary = null,
        createdAt = 2_000L,
    )

    // 1. Messages render — both USER and ASSISTANT message contents are visible.
    @Test
    fun messages_render_bothRoles() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                AgentScreenContent(
                    state = AgentUiState(
                        messages = listOf(
                            userMessage("u1", "Hello agent"),
                            assistantMessage("a1", "Hello user"),
                        ),
                    ),
                    onInput = {},
                    onSend = {},
                    onMicTap = {},
                    onNewChat = {},
                    onConsent = {},
                    onToggleInclude = {},
                    onEditWrite = { _, _ -> },
                    onApply = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithText("Hello agent").assertIsDisplayed()
        composeRule.onNodeWithText("Hello user").assertIsDisplayed()
    }

    // 2. Send fires — clicking send with non-blank input in state invokes the onSend callback.
    @Test
    fun send_firesOnSendCallback() {
        var sendFired = false
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                AgentScreenContent(
                    // Pre-populate input so send button is enabled.
                    state = AgentUiState(
                        input = "Transfer 500 to savings",
                        inFlight = false,
                    ),
                    onInput = {},
                    onSend = { sendFired = true },
                    onMicTap = {},
                    onNewChat = {},
                    onConsent = {},
                    onToggleInclude = {},
                    onEditWrite = { _, _ -> },
                    onApply = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithTag("agent_send").performClick()
        assertTrue(sendFired, "Expected onSend to fire after clicking the send button")
    }

    // 3. Mic disabled — the mic button is always disabled in M4-6.
    @Test
    fun mic_isDisabled() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                AgentScreenContent(
                    state = AgentUiState(),
                    onInput = {},
                    onSend = {},
                    onMicTap = {},
                    onNewChat = {},
                    onConsent = {},
                    onToggleInclude = {},
                    onEditWrite = { _, _ -> },
                    onApply = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithTag("agent_mic").assertIsNotEnabled()
    }

    // 4. Gate banner — when gate is Unavailable the reason text is displayed in the banner.
    @Test
    fun gateBanner_showsReasonText() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                AgentScreenContent(
                    state = AgentUiState(
                        gate = AgentAvailability.Unavailable("reason X"),
                    ),
                    onInput = {},
                    onSend = {},
                    onMicTap = {},
                    onNewChat = {},
                    onConsent = {},
                    onToggleInclude = {},
                    onEditWrite = { _, _ -> },
                    onApply = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithText("reason X").assertIsDisplayed()
    }
}
