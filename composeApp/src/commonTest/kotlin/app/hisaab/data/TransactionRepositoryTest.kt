package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewSplitTransaction
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TransactionPatch
import app.hisaab.domain.TxnKind
import app.hisaab.util.todayRangeMs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionRepositoryTest {

    private suspend fun seedAccount(db: app.hisaab.db.HisaabDatabase): String =
        AccountRepository(db).add("Cash", AccountKind.CASH, null)

    @Test
    fun `add writes a top-level txn`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        val id = txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 150.0,
                ts = todayRangeMs().first + 1000,
                merchantName = "Aarong",
                categoryId = null,
                notes = "lunch",
                kind = TxnKind.EXPENSE,
            ),
        )
        val rows = txnRepo.observeRecent(50).first()
        assertEquals(1, rows.size)
        assertEquals(id, rows[0].id)
        assertEquals(150.0, rows[0].amount)
        assertEquals(TxnKind.EXPENSE, rows[0].kind)
    }

    @Test
    fun `add with merchantName creates merchant entry`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 150.0,
                ts = 1000L,
                merchantName = "Aarong",
                categoryId = null,
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        assertEquals(1, merchantRepo.observeAll().first().size)
    }

    @Test
    fun `add with tagNames links tags`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        val id = txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 150.0,
                ts = 1000L,
                merchantName = null,
                categoryId = null,
                notes = null,
                kind = TxnKind.EXPENSE,
                tagNames = listOf("urgent", "lunch"),
            ),
        )
        val tags = tagRepo.observeTagsForTxn(id).first()
        assertEquals(2, tags.size)
    }

    @Test
    fun `observeRecent excludes child splits`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        val parentId = txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 1000.0,
                ts = 1000L,
                merchantName = null,
                categoryId = null,
                notes = "parent",
                kind = TxnKind.EXPENSE,
            ),
        )
        txnRepo.addSplits(
            parentId,
            listOf(
                NewSplitTransaction(amount = 600.0, categoryId = null, notes = "food", kind = TxnKind.EXPENSE),
                NewSplitTransaction(amount = 400.0, categoryId = null, notes = "drinks", kind = TxnKind.EXPENSE),
            ),
        )
        val top = txnRepo.observeRecent(50).first()
        assertEquals(1, top.size)
        assertEquals(parentId, top[0].id)
    }

    @Test
    fun `delete removes parent from observeRecent`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        val parentId = txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 1000.0,
                ts = 1000L,
                merchantName = null,
                categoryId = null,
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        txnRepo.addSplits(
            parentId,
            listOf(
                NewSplitTransaction(amount = 600.0, categoryId = null, notes = null, kind = TxnKind.EXPENSE),
            ),
        )
        txnRepo.delete(parentId)
        // SQLite FK CASCADE requires PRAGMA foreign_keys=ON; JdbcSqliteDriver in tests
        // does not enable it by default. We verify only that the parent is gone.
        assertEquals(0, txnRepo.observeRecent(50).first().size)
    }

    @Test
    fun `update modifies non-null patch fields`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        val id = txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 100.0,
                ts = 1000L,
                merchantName = null,
                categoryId = null,
                notes = "old",
                kind = TxnKind.EXPENSE,
            ),
        )
        txnRepo.update(id, TransactionPatch(amount = 200.0, notes = "new"))
        val row = txnRepo.observeRecent(50).first()[0]
        assertEquals(200.0, row.amount)
        assertEquals("new", row.notes)
    }

    @Test
    fun `observeTodayNet aggregates today transactions`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        val (start, _) = todayRangeMs()
        // Add today's INCOME ৳500 and EXPENSE ৳150
        txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 500.0,
                ts = start + 1000,
                merchantName = null,
                categoryId = null,
                notes = null,
                kind = TxnKind.INCOME,
            ),
        )
        txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 150.0,
                ts = start + 2000,
                merchantName = null,
                categoryId = null,
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        val net = txnRepo.observeTodayNet().first()
        assertEquals(500.0, net.income)
        assertEquals(150.0, net.expense)
        assertEquals(350.0, net.net)
    }
}
