package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import app.hisaab.domain.YearMonth
import app.hisaab.util.monthRangeMs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightRepositoryTest {

    private suspend fun setup(): Bundle {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val catRepo = CategoryRepository(db)
        catRepo.ensureDefaults()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val budgetRepo = BudgetRepository(db)
        val insightRepo = InsightRepository(db)
        val accountId = accountRepo.add("Cash", AccountKind.CASH, null)
        return Bundle(txnRepo, budgetRepo, insightRepo, accountId)
    }

    private data class Bundle(
        val txnRepo: TransactionRepository,
        val budgetRepo: BudgetRepository,
        val insightRepo: InsightRepository,
        val accountId: String,
    )

    @Test
    fun `monthlyTotals returns zero net for empty month`() = runTest {
        val b = setup()
        val totals = b.insightRepo.computeMonthlyTotals(YearMonth.of(2026, 5)).first()
        assertEquals(0.0, totals.income)
        assertEquals(0.0, totals.expense)
        assertEquals(0.0, totals.net)
        assertEquals(0.0, totals.previousMonthNet)
    }

    @Test
    fun `monthlyTotals aggregates by kind`() = runTest {
        val b = setup()
        val ym = YearMonth.of(2026, 5)
        val (start, _) = monthRangeMs(ym)
        b.txnRepo.add(
            NewTransaction(
                accountId = b.accountId,
                amount = 1000.0,
                ts = start + 1000,
                merchantName = null,
                categoryId = "salary",
                notes = null,
                kind = TxnKind.INCOME,
            ),
        )
        b.txnRepo.add(
            NewTransaction(
                accountId = b.accountId,
                amount = 300.0,
                ts = start + 2000,
                merchantName = null,
                categoryId = "food",
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        val totals = b.insightRepo.computeMonthlyTotals(ym).first()
        assertEquals(1000.0, totals.income)
        assertEquals(300.0, totals.expense)
        assertEquals(700.0, totals.net)
    }

    @Test
    fun `categoryBreakdown returns sorted slices with percentages`() = runTest {
        val b = setup()
        val ym = YearMonth.of(2026, 5)
        val (start, _) = monthRangeMs(ym)
        b.txnRepo.add(
            NewTransaction(
                accountId = b.accountId,
                amount = 600.0,
                ts = start + 1000,
                merchantName = null,
                categoryId = "food",
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        b.txnRepo.add(
            NewTransaction(
                accountId = b.accountId,
                amount = 400.0,
                ts = start + 2000,
                merchantName = null,
                categoryId = "transport",
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        val slices = b.insightRepo.computeCategoryBreakdown(ym).first()
        assertEquals(2, slices.size)
        // First slice is largest (food = 600 out of 1000 = 60%)
        assertEquals("Food & dining", slices[0].categoryName)
        assertEquals(60.0, slices[0].percent)
    }

    @Test
    fun `budgetProgress reports spent vs cap`() = runTest {
        val b = setup()
        val ym = YearMonth.of(2026, 5)
        val (start, _) = monthRangeMs(ym)
        b.budgetRepo.set("food", monthlyCapAmount = 5000.0, startsMonth = ym)
        b.txnRepo.add(
            NewTransaction(
                accountId = b.accountId,
                amount = 150.0,
                ts = start + 1000,
                merchantName = null,
                categoryId = "food",
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        val progress = b.insightRepo.computeBudgetProgress(ym, b.budgetRepo).first()
        assertEquals(1, progress.size)
        assertEquals(150.0, progress[0].spent)
        assertEquals(5000.0, progress[0].budget.monthlyCapAmount)
        assertEquals(3.0, progress[0].percent)
    }

    @Test
    fun `detectRecurring returns empty when no merchant repeats 3 or more times`() = runTest {
        val b = setup()
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        b.txnRepo.add(
            NewTransaction(
                accountId = b.accountId,
                amount = 100.0,
                ts = now,
                merchantName = "Aarong",
                categoryId = "food",
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        b.txnRepo.add(
            NewTransaction(
                accountId = b.accountId,
                amount = 100.0,
                ts = now + 1000,
                merchantName = "Aarong",
                categoryId = "food",
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        val recurring = b.insightRepo.detectRecurring().first()
        assertTrue(recurring.isEmpty())
    }

    @Test
    fun `detectRecurring returns hit when merchant repeats 3 times`() = runTest {
        val b = setup()
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        repeat(3) { i ->
            b.txnRepo.add(
                NewTransaction(
                    accountId = b.accountId,
                    amount = 100.0,
                    ts = now + i * 1000L,
                    merchantName = "Aarong",
                    categoryId = "food",
                    notes = null,
                    kind = TxnKind.EXPENSE,
                ),
            )
        }
        val recurring = b.insightRepo.detectRecurring().first()
        assertEquals(1, recurring.size)
        assertEquals("Aarong", recurring[0].merchantName)
        assertEquals(3L, recurring[0].occurrenceCount)
        assertEquals(100.0, recurring[0].avgAmount)
    }
}
