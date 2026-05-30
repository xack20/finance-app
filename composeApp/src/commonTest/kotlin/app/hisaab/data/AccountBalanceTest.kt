package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountBalanceTest {
    @Test fun `balance is income minus expense per account`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val cash = accounts.add("Cash", AccountKind.CASH, null)
        txns.add(NewTransaction(accountId = cash, amount = 1000.0, ts = 1000L, merchantName = null,
            categoryId = null, notes = null, kind = TxnKind.INCOME))
        txns.add(NewTransaction(accountId = cash, amount = 300.0, ts = 2000L, merchantName = null,
            categoryId = null, notes = null, kind = TxnKind.EXPENSE))
        val balances = accounts.accountBalances()
        assertEquals(700.0, balances[cash])
    }
}
