package app.hisaab.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.AgentQuestion
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.screens.capture.QuestionCard
import app.hisaab.screens.capture.QuestionsContent
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestionsScreenTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val card = QuestionCard(
        question = AgentQuestion("q1", "Whose card ends 1234?", listOf("Mine"), answer = null, createdAt = 1L),
        messages = listOf(
            CandidateTransaction(
                id = "m1", receivedAt = 1L, channel = CaptureChannel.EMAIL, sender = "alerts@citybank.com",
                rawBody = "Card ending 1234 charged BDT 900", dedupHash = "h", status = CaptureStatus.PENDING,
                confidence = null, parsedBy = null, model = null, parseError = null, amount = null, direction = null,
                currency = "BDT", balanceAfter = null, refNo = null, proposedAccountId = null, proposedCategoryId = null,
                proposedMerchant = null, createdAt = 1L,
            ),
        ),
    )

    private fun render(onAnswer: (String, String) -> Unit = { _, _ -> }, onNotAPayment: (String) -> Unit = {}) =
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                QuestionsContent(listOf(card), onBack = {}, onAnswer = onAnswer, onNotAPayment = onNotAPayment)
            }
        }

    @Test
    fun quickAnswer_sendsThatAnswer() {
        var sent: Pair<String, String>? = null
        render(onAnswer = { id, a -> sent = id to a })
        composeRule.onNodeWithText("Mine").performClick()
        assertEquals("q1" to "Mine", sent)
    }

    @Test
    fun typedAnswer_sendsTheText() {
        var sent: Pair<String, String>? = null
        render(onAnswer = { id, a -> sent = id to a })
        composeRule.onNodeWithText("Or type your answer").performTextInput("My wife's card")
        composeRule.onNodeWithText("Send answer").performClick()
        assertEquals("q1" to "My wife's card", sent)
    }

    @Test
    fun notAPayment_dismissesTheQuestion() {
        var dismissed: String? = null
        render(onNotAPayment = { dismissed = it })
        composeRule.onNodeWithText("Not a payment").performClick()
        assertEquals("q1", dismissed)
    }
}
