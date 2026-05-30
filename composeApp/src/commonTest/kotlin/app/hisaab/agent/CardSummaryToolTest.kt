package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertTrue

class CardSummaryToolTest {
    @Test fun `card_summary returns outstanding and available credit for a card by name`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        accounts.add("VISA", AccountKind.CARD, null, creditLimit = 50000.0, statementDay = 5, dueDay = 20)
        val cardId = accounts.observeActive().first().first { it.name == "VISA" }.id
        txns.add(NewTransaction(accountId = cardId, amount = 3000.0, ts = 1L, merchantName = null,
            categoryId = null, notes = null, kind = TxnKind.EXPENSE))
        val out = CardSummaryTool(accounts).execute(buildJsonObject { put("card", "VISA") })
        assertTrue("3000" in out)          // outstanding
        assertTrue("47000" in out)         // available = 50000 - 3000
    }
}
