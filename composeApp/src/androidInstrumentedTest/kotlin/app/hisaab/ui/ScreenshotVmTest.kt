package app.hisaab.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import app.hisaab.AppContainer
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabTheme
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.screens.LockScreen
import app.hisaab.screens.entry.EntryScreen
import app.hisaab.screens.main.MainGraph
import app.hisaab.screens.month.MonthScreen
import app.hisaab.screens.people.PeopleListScreen
import app.hisaab.screens.people.PersonDetailScreen
import app.hisaab.screens.recovery.RecoveryEntryScreen
import app.hisaab.screens.settings.AccountsScreen
import app.hisaab.screens.settings.AutoCaptureScreen
import app.hisaab.screens.settings.BudgetsScreen
import app.hisaab.screens.settings.CategoriesScreen
import app.hisaab.screens.settings.RecoveryPhraseRevealScreen
import app.hisaab.screens.settings.SettingsScreen
import app.hisaab.screens.today.TodayScreen
import app.hisaab.screens.transaction.TransactionDetailScreen
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * Data-driven screenshot tour. Builds the real Android [AppContainer] against a throwaway on-device
 * SQLCipher DB, seeds synthetic data, and renders the 16 ViewModel-backed screens (each reads
 * LocalAppContainer), writing a PNG per page. Doubles as a smoke test that every data-driven screen
 * renders without crashing against realistic data.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotVmTest {

    @get:Rule
    val rule = createAndroidComposeRule<ScreenshotHostActivity>()

    private lateinit var container: AppContainer
    private data class SeedIds(val firstTxnId: String, val firstPersonId: String)

    @After
    fun tearDown() {
        if (::container.isInitialized) container.closeDatabase()
    }

    private suspend fun seed(c: AppContainer): SeedIds {
        val accounts = c.accountRepository
        val categories = c.categoryRepository
        val txns = c.transactionRepository
        val budgets = c.budgetRepository
        val people = c.personRepository
        val lendBorrow = c.lendBorrowRepository
        val inbox = c.captureInboxRepository

        val cashAccountId = accounts.observeActive().first()
            .first { it.kind == app.hisaab.domain.AccountKind.CASH }.id

        val bankAccountId = accounts.add(
            name = "BRAC Bank", kind = app.hisaab.domain.AccountKind.BANK, institution = "BRAC Bank Ltd",
        )
        val cardAccountId = accounts.add(
            name = "City Bank Visa", kind = app.hisaab.domain.AccountKind.CARD, institution = "City Bank",
            creditLimit = 150_000.0, statementDay = 25, dueDay = 15,
        )
        val bkashAccountId = accounts.add(
            name = "bKash", kind = app.hisaab.domain.AccountKind.MFS, institution = "bKash",
        )

        val groceriesCatId = categories.add(name = "Groceries", color = "#3f7d52", icon = "🛒", parentId = null)

        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val day = 86_400_000L

        val firstTxnId = txns.add(
            app.hisaab.domain.NewTransaction(
                accountId = cardAccountId, amount = 1250.0, ts = now - 1 * day,
                merchantName = "Shwapno Supermarket", categoryId = groceriesCatId,
                source = app.hisaab.domain.TxnSource.MANUAL, notes = "Weekly groceries",
                kind = app.hisaab.domain.TxnKind.EXPENSE,
            ),
        )
        txns.add(
            app.hisaab.domain.NewTransaction(
                accountId = cashAccountId, amount = 320.0, ts = now - 2 * day,
                merchantName = "Pathao Rides", categoryId = "transport",
                source = app.hisaab.domain.TxnSource.MANUAL, notes = "Ride to office",
                kind = app.hisaab.domain.TxnKind.EXPENSE,
            ),
        )
        txns.add(
            app.hisaab.domain.NewTransaction(
                accountId = bankAccountId, amount = 1180.0, ts = now - 3 * day,
                merchantName = "DESCO", categoryId = "bills",
                source = app.hisaab.domain.TxnSource.SMS, notes = "Electricity bill",
                kind = app.hisaab.domain.TxnKind.EXPENSE,
            ),
        )
        txns.add(
            app.hisaab.domain.NewTransaction(
                accountId = bankAccountId, amount = 65_000.0, ts = now - 5 * day,
                merchantName = "Acme Corp Ltd", categoryId = "salary",
                source = app.hisaab.domain.TxnSource.SMS, notes = "May salary",
                kind = app.hisaab.domain.TxnKind.INCOME,
            ),
        )
        txns.transfer(
            fromAccountId = bankAccountId, toAccountId = bkashAccountId,
            amount = 5_000.0, ts = now - 6 * day, notes = "Top up bKash wallet",
        )

        val thisMonth = run {
            val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(now)
                .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
            app.hisaab.domain.YearMonth.of(dt.year, dt.monthNumber)
        }
        budgets.set(categoryId = groceriesCatId, monthlyCapAmount = 8_000.0, startsMonth = thisMonth)
        budgets.set(categoryId = "transport", monthlyCapAmount = 4_500.0, startsMonth = thisMonth)

        val rafiId = people.addManual("Rafi Ahmed")
        val nadiaId = people.addManual("Nadia Karim")
        lendBorrow.record(
            app.hisaab.domain.NewLendBorrow(
                personId = rafiId, amount = 3_000.0, direction = app.hisaab.domain.LendBorrowDirection.LENT,
                accountId = cashAccountId, purpose = "Lunch + cab fare", ts = now - 7 * day, dueDate = now + 14 * day,
            ),
        )
        lendBorrow.record(
            app.hisaab.domain.NewLendBorrow(
                personId = nadiaId, amount = 10_000.0, direction = app.hisaab.domain.LendBorrowDirection.BORROWED,
                accountId = bkashAccountId, purpose = "Emergency loan", ts = now - 10 * day, dueDate = now + 30 * day,
            ),
        )

        inbox.insertCandidate(
            app.hisaab.domain.CandidateTransaction(
                id = "seed-cap-1", receivedAt = now - 30 * 60_000L, channel = app.hisaab.domain.CaptureChannel.SMS,
                sender = "bKash", rawBody = "You have received Tk 1,500.00 from 01711-000000. Balance Tk 4,250.00. TrxID 9AB12CD34E",
                dedupHash = "seed-hash-cap-1", status = app.hisaab.domain.CaptureStatus.PENDING, confidence = 0.62,
                parsedBy = app.hisaab.domain.ParsedBy.TEMPLATE, model = null, parseError = null,
                amount = 1_500.0, direction = app.hisaab.domain.Direction.CREDIT, currency = "BDT",
                balanceAfter = 4_250.0, refNo = "9AB12CD34E", proposedAccountId = bkashAccountId,
                proposedCategoryId = "other", proposedMerchant = null, createdAt = now - 29 * 60_000L,
            ),
        )

        // Enable biometric so LockScreen shows the locked UI (vs. auto-unlocking when no biometric set).
        c.secureStorage.storeString("biometric_enabled", "true")

        return SeedIds(firstTxnId = firstTxnId, firstPersonId = rafiId)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        // Cold repository Flows emit shortly after subscription; let them settle + recompose.
        repeat(8) { rule.waitForIdle(); SystemClock.sleep(120) }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = File(ctx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun capture_data_driven_pages() {
        // The container is built in ScreenshotHostActivity.onCreate (ActivityResult launchers must
        // register before the activity is STARTED); read it here, then open the DB + seed.
        container = rule.activity.container
        container.openDatabase(ByteArray(32) { it.toByte() })
        val ids = runBlocking { seed(container) }

        val pages: List<Pair<String, @Composable () -> Unit>> = listOf(
            "10-main-shell" to { MainGraph() },
            "11-today" to { TodayScreen(onTxnClick = {}, onReview = {}, onAutoCapture = {}) },
            "12-month" to { MonthScreen() },
            "13-entry" to { EntryScreen(candidateId = null, onDone = {}) },
            "14-transaction-detail" to { TransactionDetailScreen(txnId = ids.firstTxnId, onDone = {}) },
            "15-settings" to {
                SettingsScreen(onAccounts = {}, onCategories = {}, onBudgets = {}, onRecoveryReveal = {}, onAutoCapture = {}, onSignedOut = {})
            },
            "16-accounts" to { AccountsScreen(onBack = {}) },
            "17-categories" to { CategoriesScreen(onBack = {}) },
            "18-budgets" to { BudgetsScreen(onBack = {}) },
            "19-auto-capture" to { AutoCaptureScreen(onBack = {}, onConsent = {}) },
            "21-people" to { PeopleListScreen(onPersonClick = {}) },
            "22-person-detail" to { PersonDetailScreen(personId = ids.firstPersonId, onBack = {}) },
            "24-recovery-reveal" to { RecoveryPhraseRevealScreen(onBack = {}) },
            "25-lock" to { LockScreen(onUnlock = {}) },
            "26-recovery-entry" to { RecoveryEntryScreen(onRecovered = {}) },
        )

        var idx by mutableIntStateOf(0)
        rule.setContent {
            HisaabTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Box(Modifier.fillMaxSize().background(LocalHisaabPalette.current.background)) {
                        pages[idx].second()
                    }
                }
            }
        }

        for (i in pages.indices) {
            rule.runOnUiThread { idx = i }
            capture(pages[i].first)
        }
        println("ScreenshotVm wrote ${pages.size} data-driven pages")
    }
}
