package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.LendBorrowStatus
import app.hisaab.domain.NewLendBorrow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LendBorrowRepositoryTest {

    private suspend fun setup(): SetupBundle {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val personRepo = PersonRepository(db)
        val lendBorrowRepo = LendBorrowRepository(db, txnRepo)
        // Seed defaults: account + a couple of categories so settle's "transfer" category exists.
        // For tests we can rely on the seed running once via CategoryRepository.ensureDefaults().
        val catRepo = CategoryRepository(db)
        catRepo.ensureDefaults()
        val accountId = accountRepo.add("Cash", AccountKind.CASH, null)
        val personId = personRepo.addManual("Karim")
        return SetupBundle(db, txnRepo, lendBorrowRepo, accountId, personId)
    }

    private data class SetupBundle(
        val db: app.hisaab.db.HisaabDatabase,
        val txnRepo: TransactionRepository,
        val lendBorrowRepo: LendBorrowRepository,
        val accountId: String,
        val personId: String,
    )

    @Test
    fun `record writes lend_borrow plus linked txn`() = runTest {
        val b = setup()
        val (lendBorrowId, txnId) = b.lendBorrowRepo.record(
            NewLendBorrow(
                personId = b.personId,
                amount = 500.0,
                direction = LendBorrowDirection.LENT,
                accountId = b.accountId,
                purpose = "lunch loan",
                ts = 1000L,
                dueDate = null,
            ),
        )
        assertTrue(lendBorrowId.isNotBlank())
        assertTrue(txnId.isNotBlank())
        assertEquals(1, b.txnRepo.observeRecent(50).first().size)
        assertEquals(1, b.lendBorrowRepo.observeOpen().first().size)
    }

    @Test
    fun `settle full amount sets status SETTLED`() = runTest {
        val b = setup()
        val (lendBorrowId, _) = b.lendBorrowRepo.record(
            NewLendBorrow(
                personId = b.personId, amount = 500.0,
                direction = LendBorrowDirection.LENT,
                accountId = b.accountId, purpose = null,
                ts = 1000L, dueDate = null,
            ),
        )
        b.lendBorrowRepo.settle(lendBorrowId, settlementAmount = 500.0, accountId = b.accountId)
        val records = b.lendBorrowRepo.observeOpen().first()
        assertEquals(0, records.size)  // no longer open
    }

    @Test
    fun `settle partial sets status PARTIAL — still open`() = runTest {
        val b = setup()
        val (lendBorrowId, _) = b.lendBorrowRepo.record(
            NewLendBorrow(
                personId = b.personId, amount = 500.0,
                direction = LendBorrowDirection.LENT,
                accountId = b.accountId, purpose = null,
                ts = 1000L, dueDate = null,
            ),
        )
        b.lendBorrowRepo.settle(lendBorrowId, settlementAmount = 200.0, accountId = b.accountId)
        val open = b.lendBorrowRepo.observeOpen().first()
        assertEquals(1, open.size)
        assertEquals(LendBorrowStatus.PARTIAL, open[0].status)
    }

    @Test
    fun `observeForPerson returns the records for that person`() = runTest {
        val b = setup()
        b.lendBorrowRepo.record(
            NewLendBorrow(
                personId = b.personId, amount = 100.0,
                direction = LendBorrowDirection.LENT,
                accountId = b.accountId, purpose = null,
                ts = 1000L, dueDate = null,
            ),
        )
        b.lendBorrowRepo.record(
            NewLendBorrow(
                personId = b.personId, amount = 200.0,
                direction = LendBorrowDirection.BORROWED,
                accountId = b.accountId, purpose = null,
                ts = 2000L, dueDate = null,
            ),
        )
        val records = b.lendBorrowRepo.observeForPerson(b.personId).first()
        assertEquals(2, records.size)
        assertTrue(records.all { it.personName == "Karim" })
    }
}
