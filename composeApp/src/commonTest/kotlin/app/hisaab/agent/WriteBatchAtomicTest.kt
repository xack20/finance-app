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
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WriteBatchAtomicTest {
    private fun committer(db: HisaabDatabase): WriteBatchCommitter {
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        return WriteBatchCommitter(db, AccountRepository(db), CategoryRepository(db),
            PersonRepository(db), txns, LendBorrowRepository(db, txns))
    }

    @Test fun `mixed batch commits all rows together`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()   // seed "lend"/"borrow"/"transfer" FK categories
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val bank = accounts.add("Bank", AccountKind.BANK, null)
        accounts.add("VISA", AccountKind.CARD, null)
        val summary = committer(db).apply(listOf(
            ProposedWrite("create_account", buildJsonObject { put("name", "Cash"); put("kind", "CASH") }),
            ProposedWrite("add_transaction", buildJsonObject { put("account", "Cash"); put("amount", 300.0); put("kind", "EXPENSE") }),
            ProposedWrite("record_lend_borrow", buildJsonObject {
                put("person", "Karim"); put("amount", 2000.0); put("kind", "LENT"); put("fromAccount", "Cash") }),
            ProposedWrite("record_card_payment", buildJsonObject { put("fromAccount", "Bank"); put("card", "VISA"); put("amount", 1500.0) }),
        ))
        assertEquals(1, summary.accountsCreated)
        assertEquals(1, summary.transactionsAdded)
        assertEquals(1, summary.lendBorrowsRecorded)
        assertEquals(1, summary.transfers)
        // expense(1) + lend leg(1) + transfer pair(2) = 4 top-level rows
        assertEquals(4, txns.observeRecent(20).first().size)
    }

    @Test fun `a failed write rolls the whole batch back`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        // Second write references an account never created → resolution throws → rollback.
        assertFailsWith<IllegalArgumentException> {
            committer(db).apply(listOf(
                ProposedWrite("create_account", buildJsonObject { put("name", "Cash"); put("kind", "CASH") }),
                ProposedWrite("add_transaction", buildJsonObject { put("account", "Ghost"); put("amount", 99.0); put("kind", "EXPENSE") }),
            ))
        }
        assertTrue(AccountRepository(db).observeActive().first().none { it.name == "Cash" })
        assertEquals(0, txns.observeRecent(20).first().size)
    }
}
