package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.db.HisaabDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class WriteBatchCommitterTest {
    private fun committer(db: HisaabDatabase): WriteBatchCommitter {
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        return WriteBatchCommitter(db, AccountRepository(db), CategoryRepository(db),
            PersonRepository(db), txns, LendBorrowRepository(db, txns))
    }

    @Test fun `applies a create_account then add_transaction referencing it by name`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val summary = committer(db).apply(listOf(
            ProposedWrite("create_account", buildJsonObject { put("name", "Wallet"); put("kind", "CASH") }),
            ProposedWrite("add_transaction", buildJsonObject {
                put("account", "Wallet"); put("amount", 250.0); put("kind", "EXPENSE"); put("notes", "snacks")
            }),
        ))
        assertEquals(1, summary.accountsCreated)
        assertEquals(1, summary.transactionsAdded)
        val row = txns.observeRecent(10).first().single()
        assertEquals(250.0, row.amount)
    }
}
