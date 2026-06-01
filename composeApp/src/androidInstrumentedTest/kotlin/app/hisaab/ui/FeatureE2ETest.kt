package app.hisaab.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import app.hisaab.AppContainer
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabTheme
import app.hisaab.domain.AccountKind
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.ParsedBy
import app.hisaab.domain.TxnKind
import app.hisaab.screens.capture.ReviewInboxScreen
import app.hisaab.screens.entry.EntryScreen
import app.hisaab.screens.main.MainGraph
import app.hisaab.screens.people.PeopleListScreen
import app.hisaab.screens.settings.AccountsScreen
import app.hisaab.screens.settings.BudgetsScreen
import app.hisaab.screens.settings.CategoriesScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end flow tests for the authenticated app: each drives the REAL screen + ViewModel against a
 * fresh, real (SQLCipher) on-device DB and verifies persistence via the real repositories. Each test
 * starts from a clean DB (deleted in @Before) so assertions are deterministic.
 */
@OptIn(ExperimentalTestApi::class)
class FeatureE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ScreenshotHostActivity>()

    private lateinit var container: AppContainer
    private val secret = ByteArray(32) { it.toByte() }

    @Before
    fun freshDb() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("hisaab.db")
    }

    @After
    fun tearDown() {
        if (::container.isInitialized) container.closeDatabase()
    }

    /** Open a fresh DB and render [content] wrapped in theme + container. */
    private fun open(content: @Composable () -> Unit) {
        container = composeRule.activity.container
        container.openDatabase(secret)
        composeRule.setContent {
            HisaabTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppContainer provides container) { content() }
            }
        }
    }

    private fun settle(times: Int = 6) {
        repeat(times) { composeRule.waitForIdle(); SystemClock.sleep(120) }
    }

    @Test
    fun add_transaction_persists_to_ledger() {
        open { EntryScreen(candidateId = null, onDone = {}) }
        settle()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("500")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Account").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cash").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Category").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Food & dining").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performClick()
        settle()

        val rows = runBlocking { container.transactionRepository.observeRecent(10).first() }
        assertTrue("expected a saved txn of amount 500", rows.any { it.amount == 500.0 })
        val saved = rows.first { it.amount == 500.0 }
        assertEquals(TxnKind.EXPENSE, saved.kind)
        assertEquals("food", saved.categoryId)
    }

    @Test
    fun navigation_across_bottom_nav_tabs() {
        open { MainGraph() }
        settle()
        composeRule.onNodeWithText("No entries yet", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("dock_MONTH").performClick(); settle(4)
        // Per-day chart moved inside the Net card; assert the Net card eyebrow instead.
        composeRule.onNodeWithText("NET THIS MONTH").assertIsDisplayed()

        composeRule.onNodeWithTag("dock_PEOPLE").performClick(); settle(4)
        composeRule.onNodeWithText("No one yet", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("dock_SETTINGS").performClick(); settle(4)
        composeRule.onNodeWithText("Sign out").assertIsDisplayed()

        composeRule.onNodeWithTag("dock_TODAY").performClick(); settle(4)
        composeRule.onNodeWithText("No entries yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun add_account_persists() {
        open { AccountsScreen(onBack = {}) }
        settle()
        composeRule.onNodeWithText("+ Add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New account").assertIsDisplayed()
        composeRule.onNodeWithText("Name").performTextInput("Emergency Fund")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Add").filterToOne(hasClickAction()).performClick()
        settle()

        val accounts = runBlocking { container.accountRepository.observeActive().first() }
        assertTrue(accounts.any { it.name == "Emergency Fund" && it.kind == AccountKind.CASH })
        composeRule.onNodeWithText("Emergency Fund").assertIsDisplayed()
    }

    @Test
    fun add_category_persists() {
        open { CategoriesScreen(onBack = {}) }
        settle()
        composeRule.onNodeWithText("+ Add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New category").assertIsDisplayed()
        composeRule.onNodeWithText("Name").performTextInput("Subscriptions")
        // The emoji field is gone — the icon is chosen from a glyph grid (a default icon is pre-selected).
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Add category").filterToOne(hasClickAction()).performClick()
        settle()

        val categories = runBlocking { container.categoryRepository.observeAll().first() }
        assertTrue(categories.any { it.name == "Subscriptions" })
        composeRule.onNodeWithText("Subscriptions").assertIsDisplayed()
    }

    @Test
    fun add_budget_persists() {
        open { BudgetsScreen(onBack = {}) }
        settle()
        composeRule.onNodeWithText("+ Add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New budget").assertIsDisplayed()
        // The category RadioButtons carry the onClick (not the name Text), and all rows are flat
        // siblings, so a sibling matcher is ambiguous — select the first category radio.
        composeRule.onAllNodes(isSelectable()).onFirst().performClick()
        composeRule.waitForIdle()
        // The cap is now a MidnightTextField ("Monthly cap", ৳ prefix); target the single editable field.
        composeRule.onNode(hasSetTextAction()).performTextInput("4500")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save budget").performClick()
        settle()

        val budgets = runBlocking { container.budgetRepository.observeActive().first() }
        assertTrue("a budget with cap 4500 should be created", budgets.any { it.monthlyCapAmount == 4500.0 })
    }

    @Test
    fun confirm_capture_candidate_posts_to_ledger() {
        container = composeRule.activity.container
        container.openDatabase(secret)
        runBlocking {
            val cashId = container.accountRepository.observeActive().first()
                .first { it.kind == AccountKind.CASH }.id
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            container.captureInboxRepository.insertCandidate(
                CandidateTransaction(
                    id = "cap-confirm-1", receivedAt = now - 1_800_000L, channel = CaptureChannel.SMS,
                    sender = "bKash", rawBody = "You have received Tk 1,500.00 from 01711-000000. TrxID 9AB12CD34E",
                    dedupHash = "hash-cap-confirm-1", status = CaptureStatus.PENDING, confidence = 0.62,
                    parsedBy = ParsedBy.TEMPLATE, model = null, parseError = null,
                    amount = 1_500.0, direction = Direction.CREDIT, currency = "BDT",
                    balanceAfter = 4_250.0, refNo = "9AB12CD34E", proposedAccountId = cashId,
                    proposedCategoryId = "other", proposedMerchant = null, createdAt = now - 1_700_000L,
                )
            )
        }
        composeRule.setContent {
            HisaabTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    ReviewInboxScreen(onBack = {}, onEdit = {})
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Confirm").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodesWithText("Confirm").filterToOne(hasClickAction()).performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { container.transactionRepository.observeRecent(50).first() }
                .any { it.kind == TxnKind.INCOME && it.amount == 1_500.0 }
        }

        val txns = runBlocking { container.transactionRepository.observeRecent(50).first() }
        assertTrue(txns.any { it.kind == TxnKind.INCOME && it.amount == 1_500.0 })
        assertEquals(
            CaptureStatus.CONFIRMED,
            runBlocking { container.captureInboxRepository.getById("cap-confirm-1")?.status },
        )
    }

    @Test
    fun lend_recording_reflects_in_people_balance() {
        // Record a lend through the real repository (which also posts a linked LEND txn), then render
        // the real People screen and assert the computed balance is shown. Exercises the lend feature
        // end-to-end: record → balance computation → UI. (The manual-entry UI path is covered by
        // add_transaction; the lend entry goes through a modal sheet not reliably driven in-harness.)
        container = composeRule.activity.container
        container.openDatabase(secret)
        runBlocking {
            val cashId = container.accountRepository.observeActive().first()
                .first { it.kind == AccountKind.CASH }.id
            val personId = container.personRepository.addManual("Imran Khan")
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            container.lendBorrowRepository.record(
                app.hisaab.domain.NewLendBorrow(
                    personId = personId, amount = 500.0, direction = LendBorrowDirection.LENT,
                    accountId = cashId, purpose = "Lunch", ts = now, dueDate = null,
                )
            )
        }
        composeRule.setContent {
            HisaabTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    PeopleListScreen(onPersonClick = {})
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Imran Khan").fetchSemanticsNodes().isNotEmpty() }

        // UI shows the person + the lent balance (PeopleListScreen renders "+৳500" for a LENT balance).
        composeRule.onNodeWithText("Imran Khan").assertIsDisplayed()
        composeRule.onNodeWithText("+৳500").assertIsDisplayed()
        // Repo cross-checks: positive balance + a linked LEND transaction.
        val imran = runBlocking { container.personRepository.observeAll().first() }
            .first { it.person.name == "Imran Khan" }
        assertEquals(500.0, imran.balance, 0.001)
        val txns = runBlocking { container.transactionRepository.observeRecent(50).first() }
        assertTrue(txns.any { it.kind == TxnKind.LEND && it.amount == 500.0 })
    }
}
