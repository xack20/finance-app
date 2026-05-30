package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransferBlockingTest {
    @Test fun `transfer inserts two TRANSFER legs sharing a group id`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        CategoryRepository(db).ensureDefaults()
        val bank = accounts.add("Bank", AccountKind.BANK, null)
        val cash = accounts.add("Cash", AccountKind.CASH, null)
        lateinit var groupId: String
        db.transaction { groupId = txns.transferBlocking(bank, cash, 500.0, ts = 10L, notes = null) }
        val rows = txns.observeRecent(10).first()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.kind == TxnKind.TRANSFER })
        assertTrue(rows.all { it.transferGroupId == groupId })
        assertNotNull(rows.firstOrNull { it.accountId == bank })
        assertNotNull(rows.firstOrNull { it.accountId == cash })
    }
}
