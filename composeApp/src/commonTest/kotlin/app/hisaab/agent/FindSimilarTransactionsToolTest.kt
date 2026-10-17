package app.hisaab.agent

import app.hisaab.data.AccountRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindSimilarTransactionsToolTest {

    @Test
    fun `finds same-amount payments within two days of the date`() = runTest {
        val db = TestDatabase.create()
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val cash = AccountRepository(db).ensureDefaultCashAccount()
        fun day(d: Int) = LocalDate(2025, 3, d).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 10 * 3_600_000
        suspend fun add(amount: Double, d: Int, merchant: String) = txns.add(
            NewTransaction(cash, amount, ts = day(d), merchantName = merchant, categoryId = null, notes = null,
                kind = TxnKind.EXPENSE),
        )
        add(1200.0, 4, "Daraz")   // match
        add(1200.0, 12, "Shwapno") // too far away
        add(999.0, 4, "Agora")     // other amount

        val out = FindSimilarTransactionsTool(txns, TimeZone.UTC)
            .execute(Json.parseToJsonElement("""{"amount":1200,"date":"2025-03-05"}""").jsonObject)

        assertTrue("Daraz" in out, out)
        assertFalse("Shwapno" in out, out)
        assertFalse("Agora" in out, out)
    }

    @Test
    fun `several payments can be checked in one lookup`() = runTest {
        val db = TestDatabase.create()
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val cash = AccountRepository(db).ensureDefaultCashAccount()
        val ts = LocalDate(2025, 3, 4).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        txns.add(NewTransaction(cash, 1200.0, ts = ts, merchantName = "Daraz", categoryId = null, notes = null, kind = TxnKind.EXPENSE))
        txns.add(NewTransaction(cash, 80.0, ts = ts, merchantName = "Pathao", categoryId = null, notes = null, kind = TxnKind.EXPENSE))

        val out = FindSimilarTransactionsTool(txns, TimeZone.UTC).execute(
            Json.parseToJsonElement(
                """{"items":[{"amount":1200,"date":"2025-03-04"},{"amount":80,"date":"2025-03-05"},{"amount":5,"date":"2025-03-05"}]}""",
            ).jsonObject,
        )

        assertTrue("Daraz" in out && "Pathao" in out, out)
    }
}

