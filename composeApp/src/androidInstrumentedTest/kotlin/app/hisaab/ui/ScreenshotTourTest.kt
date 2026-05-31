package app.hisaab.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import app.hisaab.agent.AgentAvailability
import app.hisaab.design.HisaabTheme
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.AgentMessage
import app.hisaab.domain.AgentRole
import app.hisaab.screens.agent.AgentScreenContent
import app.hisaab.screens.agent.AgentUiState
import app.hisaab.screens.onboarding.BiometricSetupScreen
import app.hisaab.screens.onboarding.OtpScreen
import app.hisaab.screens.onboarding.ProfileSetupScreen
import app.hisaab.screens.onboarding.RecoveryPhraseScreen
import app.hisaab.screens.SplashScreen
import app.hisaab.screens.onboarding.WelcomeScreen
import app.hisaab.agent.ProposedWrite
import app.hisaab.domain.BudgetProgress
import app.hisaab.domain.BudgetRow
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.CategorySlice
import app.hisaab.domain.DayBucket
import app.hisaab.domain.Direction
import app.hisaab.domain.EngineMode
import app.hisaab.domain.ParsedBy
import app.hisaab.domain.RecurringHit
import app.hisaab.domain.YearMonth
import app.hisaab.screens.agent.ReviewCard
import app.hisaab.screens.capture.ReviewCandidate
import app.hisaab.screens.capture.ReviewInboxContent
import app.hisaab.screens.month.BudgetProgressList
import app.hisaab.screens.month.CategoryBarChart
import app.hisaab.screens.month.PerDayLineChart
import app.hisaab.screens.month.RecurringList
import app.hisaab.screens.onboarding.CaptureOptInCard
import app.hisaab.screens.settings.CloudConsentScreen
import app.hisaab.screens.settings.EnginePicker
import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot tour — renders each app page with synthetic state and writes a PNG per page to the
 * app's external files dir (pulled to docs/design/screenshots/android/ afterwards). Doubles as a
 * smoke test that every catalogued stateless page renders without crashing.
 *
 * Batch 1: onboarding funnel + Assistant chat (stateless, zero domain-type construction).
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTourTest {

    @get:Rule
    val rule = createComposeRule()

    private fun user(id: String, content: String) = AgentMessage(
        id = id, conversationId = "conv", role = AgentRole.USER, content = content,
        proposedWrites = emptyList(), appliedSummary = null, createdAt = 1_000L,
    )

    private fun assistant(id: String, content: String) = AgentMessage(
        id = id, conversationId = "conv", role = AgentRole.ASSISTANT, content = content,
        proposedWrites = emptyList(), appliedSummary = null, createdAt = 2_000L,
    )

    private val recoveryWords = listOf(
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
        "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
        "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
    )

    private val noAgent: @Composable (AgentUiState) -> Unit = { state ->
        AgentScreenContent(
            state = state,
            onInput = {}, onSend = {}, onMicTap = {}, onNewChat = {}, onConsent = {},
            onToggleInclude = {}, onEditWrite = { _, _ -> }, onApply = {}, onClose = {},
        )
    }

    @Test
    fun capture_pages() {
        val pages: List<Pair<String, @Composable () -> Unit>> = listOf(
            "00-splash" to { SplashScreen() },
            "01-welcome-default" to { WelcomeScreen(onSendOtp = {}) },
            "01-welcome-loading" to { WelcomeScreen(onSendOtp = {}, isLoading = true) },
            "01-welcome-error" to { WelcomeScreen(onSendOtp = {}, error = "Couldn't send code. Try again.") },
            "02-otp-default" to { OtpScreen(phone = "+8801712345678", onVerify = {}, onResend = {}) },
            "02-otp-error" to { OtpScreen(phone = "+8801712345678", onVerify = {}, onResend = {}, error = "Invalid code") },
            "03-biometric-available" to { BiometricSetupScreen(isAvailable = true, onEnroll = {}, onSkip = {}) },
            "03-biometric-unavailable" to { BiometricSetupScreen(isAvailable = false, onEnroll = {}, onSkip = {}) },
            "04-recovery-phrase" to { RecoveryPhraseScreen(words = recoveryWords, onAcknowledged = {}) },
            "05-profile" to { ProfileSetupScreen(onComplete = { _, _ -> }) },
            "20-assistant-empty" to { noAgent(AgentUiState()) },
            "20-assistant-chat" to {
                noAgent(
                    AgentUiState(
                        messages = listOf(
                            user("u1", "How much did I spend on food this month?"),
                            assistant("a1", "You've spent ৳12,000 on Food — about 38% of your spending so far. Want me to set a monthly budget?"),
                        ),
                    )
                )
            },
            "20-assistant-unavailable" to {
                noAgent(AgentUiState(gate = AgentAvailability.Unavailable("Add an API key in Settings to use the assistant.")))
            },
            "06-capture-optin" to { CaptureOptInCard(onTurnOn = {}, onMaybeLater = {}) },
            "23-review-inbox" to {
                ReviewInboxContent(
                    pending = listOf(
                        ReviewCandidate(
                            candidate = CandidateTransaction(
                                id = "c1", receivedAt = 1748390400000L, channel = CaptureChannel.SMS,
                                sender = "bKash", rawBody = "You have received Tk 1,200.00 from 01712345678. Ref 9XK2.",
                                dedupHash = "h1", status = CaptureStatus.PENDING, confidence = 0.92,
                                parsedBy = ParsedBy.ON_DEVICE, model = null, parseError = null,
                                amount = 1200.0, direction = Direction.CREDIT, currency = "BDT",
                                balanceAfter = 5400.0, refNo = "9XK2", proposedAccountId = "acc_bkash",
                                proposedCategoryId = "income", proposedMerchant = "Rahim", createdAt = 1748390400000L,
                            ),
                            accountName = "bKash", categoryName = "Income",
                        ),
                        ReviewCandidate(
                            candidate = CandidateTransaction(
                                id = "c2", receivedAt = 1748131200000L, channel = CaptureChannel.SMS,
                                sender = "BRAC BANK", rawBody = "Debit Tk 850.00 at SHWAPNO on 25-May. Avl bal Tk 12,300.",
                                dedupHash = "h2", status = CaptureStatus.PENDING, confidence = 0.64,
                                parsedBy = ParsedBy.TEMPLATE, model = null, parseError = null,
                                amount = 850.0, direction = Direction.DEBIT, currency = "BDT",
                                balanceAfter = 12300.0, refNo = null, proposedAccountId = "acc_brac",
                                proposedCategoryId = null, proposedMerchant = "Shwapno", createdAt = 1748131200000L,
                            ),
                            accountName = "BRAC Bank", categoryName = null,
                        ),
                    ),
                    onBack = {}, onConfirm = {}, onConfirmAllHighConfidence = {}, onDismiss = {}, onEdit = {},
                )
            },
            "30-chart-category" to {
                CategoryBarChart(
                    slices = listOf(
                        CategorySlice(categoryId = "food", categoryName = "Food", categoryColor = "#E5484D", total = 4200.0, percent = 42.0),
                        CategorySlice(categoryId = "transport", categoryName = "Transport", categoryColor = "#3E63DD", total = 2500.0, percent = 25.0),
                        CategorySlice(categoryId = "bills", categoryName = "Bills", categoryColor = null, total = 1800.0, percent = 18.0),
                        CategorySlice(categoryId = "shopping", categoryName = "Shopping", categoryColor = "#30A46C", total = 1500.0, percent = 15.0),
                    ),
                    palette = LocalHisaabPalette.current,
                )
            },
            "31-chart-perday" to {
                PerDayLineChart(
                    buckets = listOf(
                        DayBucket(epochDay = 20240L, total = 350.0),
                        DayBucket(epochDay = 20243L, total = 1200.0),
                        DayBucket(epochDay = 20247L, total = 600.0),
                        DayBucket(epochDay = 20251L, total = 2100.0),
                        DayBucket(epochDay = 20255L, total = 900.0),
                        DayBucket(epochDay = 20260L, total = 1500.0),
                    ),
                    palette = LocalHisaabPalette.current,
                )
            },
            "32-budget-progress" to {
                BudgetProgressList(
                    budgets = listOf(
                        BudgetProgress(budget = BudgetRow(id = "b1", categoryId = "food", categoryName = "Food", monthlyCapAmount = 6000.0, currency = "BDT", startsMonth = YearMonth.of(2026, 5), archivedAt = null, createdAt = 0L), spent = 3200.0, percent = 53.0),
                        BudgetProgress(budget = BudgetRow(id = "b2", categoryId = "transport", categoryName = "Transport", monthlyCapAmount = 2500.0, currency = "BDT", startsMonth = YearMonth.of(2026, 5), archivedAt = null, createdAt = 0L), spent = 2100.0, percent = 84.0),
                        BudgetProgress(budget = BudgetRow(id = "b3", categoryId = "shopping", categoryName = "Shopping", monthlyCapAmount = 1500.0, currency = "BDT", startsMonth = YearMonth.of(2026, 5), archivedAt = null, createdAt = 0L), spent = 1800.0, percent = 120.0),
                    ),
                    palette = LocalHisaabPalette.current,
                )
            },
            "33-recurring" to {
                RecurringList(
                    items = listOf(
                        RecurringHit(merchantId = "m1", merchantName = "Netflix", occurrenceCount = 6L, avgAmount = 1100.0, lastSeenTs = 1748390400000L),
                        RecurringHit(merchantId = "m2", merchantName = "Grameenphone", occurrenceCount = 4L, avgAmount = 450.0, lastSeenTs = 1748131200000L),
                        RecurringHit(merchantId = "m3", merchantName = "Daraz", occurrenceCount = 3L, avgAmount = 2300.0, lastSeenTs = 1747526400000L),
                    ),
                    palette = LocalHisaabPalette.current,
                )
            },
            "41-review-card" to {
                ReviewCard(
                    writes = listOf(
                        ProposedWrite(tool = "add_transaction", args = buildJsonObject { put("kind", "expense"); put("amount", "500"); put("account", "Cash"); put("category", "Food") }),
                        ProposedWrite(tool = "transfer", args = buildJsonObject { put("amount", "1000"); put("fromAccount", "Bank"); put("toAccount", "Cash") }),
                        ProposedWrite(tool = "set_budget", args = buildJsonObject { put("category", "Food"); put("amount", "8000") }),
                    ),
                    included = setOf(0, 1),
                    onToggle = {}, onEdit = { _, _ -> }, onApply = {},
                )
            },
            "44-cloud-consent" to {
                CloudConsentScreen(providerName = "Claude", consentGranted = false, onGrant = {}, onRevoke = {}, onBack = {})
            },
            "45-engine-picker" to {
                EnginePicker(mode = EngineMode.ON_DEVICE, palette = LocalHisaabPalette.current, onSelect = {})
            },
        )

        var idx by mutableIntStateOf(0)
        rule.setContent {
            HisaabTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(LocalHisaabPalette.current.background)) {
                    pages[idx].second()
                }
            }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = File(ctx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

        for (i in pages.indices) {
            rule.runOnUiThread { idx = i }
            rule.waitForIdle()
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(outDir, pages[i].first + ".png").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        println("ScreenshotTour wrote ${pages.size} pages to ${outDir.absolutePath}")
    }
}
