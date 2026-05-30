package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import app.hisaab.domain.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CardOutstandingTest {
    @Test fun `purchase raises outstanding and counts as spend - payment lowers outstanding and is not spend`() = runTest {
        val db = TestDatabase.create()
        val categories = CategoryRepository(db)
        categories.ensureDefaults()                       // seed "transfer" (payment leg FK)
        val foodId = categories.add("Food", null, null, null)   // explicit category for the purchase
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val insights = InsightRepository(db)
        val card = accounts.add("VISA", AccountKind.CARD, null, creditLimit = 50000.0, statementDay = 5, dueDay = 20)
        val bank = accounts.add("Bank", AccountKind.BANK, null)

        val ms = 1_770_000_000_000L
        val ym = monthOf(ms)

        // card PURCHASE: EXPENSE on the card.
        txns.add(NewTransaction(accountId = card, amount = 3000.0, ts = ms, merchantName = null,
            categoryId = foodId, notes = null, kind = TxnKind.EXPENSE))
        assertEquals(3000.0, accounts.cardOutstanding(card))
        assertEquals(3000.0, monthlySpend(insights, ym))   // purchase counts as spend

        // card BILL PAYMENT: TRANSFER bank → card.
        db.transaction { txns.transferBlocking(bank, card, 2000.0, ts = ms, notes = null) }
        assertEquals(1000.0, accounts.cardOutstanding(card))  // 3000 − 2000
        assertEquals(3000.0, monthlySpend(insights, ym))      // UNCHANGED — payment is not spend
    }

    private suspend fun monthlySpend(insights: InsightRepository, ym: YearMonth): Double =
        insights.computeCategoryBreakdown(ym).first().sumOf { it.total }

    private fun monthOf(ms: Long): YearMonth {
        val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return YearMonth.of(ldt.year, ldt.monthNumber)
    }
}
