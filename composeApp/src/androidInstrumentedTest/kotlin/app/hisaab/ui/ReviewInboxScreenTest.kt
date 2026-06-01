package app.hisaab.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.ParsedBy
import app.hisaab.screens.capture.ReviewCandidate
import app.hisaab.screens.capture.ReviewInboxContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReviewInboxScreenTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private fun reviewCandidate(id: String) = ReviewCandidate(
        candidate = CandidateTransaction(
            id = id, receivedAt = 1L, channel = CaptureChannel.SMS, sender = "bKash",
            rawBody = "You have received Tk 500", dedupHash = "h-$id",
            status = CaptureStatus.PENDING, confidence = 0.6, parsedBy = ParsedBy.TEMPLATE,
            model = null, parseError = null, amount = 500.0, direction = Direction.CREDIT,
            currency = "BDT", balanceAfter = null, refNo = null, proposedAccountId = "acc",
            proposedCategoryId = null, proposedMerchant = "bKash", createdAt = 1L,
        ),
        accountName = "Cash",
        categoryName = null,
    )

    @Test
    fun confirmButton_invokesOnConfirmWithCandidateId() {
        var confirmed: String? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                ReviewInboxContent(
                    pending = listOf(reviewCandidate("c1")),
                    onBack = {},
                    onConfirm = { confirmed = it },
                    onConfirmAllHighConfidence = {},
                    onDismiss = {},
                    onEdit = {},
                )
            }
        }
        composeRule.onNodeWithText("Confirm").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").performClick()
        assertEquals("c1", confirmed)
    }

    @Test
    fun swipeLeft_invokesOnDismissWithCandidateId() {
        var dismissed: String? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                ReviewInboxContent(
                    pending = listOf(reviewCandidate("c2")),
                    onBack = {},
                    onConfirm = {},
                    onConfirmAllHighConfidence = {},
                    onDismiss = { dismissed = it },
                    onEdit = {},
                )
            }
        }
        // Swipe the amount text node left to trigger the dismiss gesture.
        composeRule.onNodeWithText("+৳500").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals("c2", dismissed)
    }

    @Test
    fun emptyState_rendersCopy_inRealScreen() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                ReviewInboxContent(
                    pending = emptyList(),
                    onBack = {},
                    onConfirm = {},
                    onConfirmAllHighConfidence = {},
                    onDismiss = {},
                    onEdit = {},
                )
            }
        }
        composeRule.onNodeWithText("Nothing to review", substring = true).assertIsDisplayed()
        assertTrue(true)
    }
}
