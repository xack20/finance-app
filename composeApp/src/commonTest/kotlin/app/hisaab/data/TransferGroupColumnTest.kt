package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class TransferGroupColumnTest {
    @Test fun `recent txns expose a nullable transferGroupId`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val cash = accounts.add("Cash", AccountKind.CASH, null)
        txns.add(NewTransaction(accountId = cash, amount = 100.0, ts = 1L, merchantName = null,
            categoryId = null, notes = null, kind = TxnKind.EXPENSE))
        val row = txns.observeRecent(10).first().single()
        assertNull(row.transferGroupId)
    }
}
