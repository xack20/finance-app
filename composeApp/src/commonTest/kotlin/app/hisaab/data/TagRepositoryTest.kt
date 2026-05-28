package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagRepositoryTest {

    @Test
    fun `upsertByName dedups exact-string matches`() = runTest {
        val repo = TagRepository(TestDatabase.create())
        val a = repo.upsertByName("urgent")
        val b = repo.upsertByName("urgent")
        assertEquals(a, b)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `upsertByName is case-sensitive`() = runTest {
        val repo = TagRepository(TestDatabase.create())
        val a = repo.upsertByName("Urgent")
        val b = repo.upsertByName("urgent")
        assertTrue(a != b)
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `linkToTxn links and unlinks correctly`() = runTest {
        val db = TestDatabase.create()
        val repo = TagRepository(db)
        // Insert a txn row directly via the SQLDelight query so linkToTxn has something to FK to.
        // (The full TransactionRepository comes in Task 12; here we just need a txn row.)
        // First need an account because txn.account_id has FK constraint.
        val accountRepo = AccountRepository(db)
        val accId = accountRepo.add("Cash", AccountKind.CASH, null)
        val txnId = "test-txn-id"
        db.transactionQueriesQueries.insertTxn(
            id = txnId,
            account_id = accId,
            amount = 100.0,
            currency = "BDT",
            ts = 0L,
            merchant_id = null,
            category_id = null,
            source = TxnSource.MANUAL.name,
            notes = null,
            kind = TxnKind.EXPENSE.name,
            parent_txn_id = null,
            capture_id = null,
        )

        val t1 = repo.upsertByName("urgent")
        val t2 = repo.upsertByName("travel")
        repo.linkToTxn(txnId, listOf(t1, t2))

        val tags = repo.observeTagsForTxn(txnId).first()
        assertEquals(2, tags.size)

        // Re-link with only t1 — t2 should be removed.
        repo.linkToTxn(txnId, listOf(t1))
        val tagsAfter = repo.observeTagsForTxn(txnId).first()
        assertEquals(1, tagsAfter.size)
        assertEquals(t1, tagsAfter[0].id)
    }
}
