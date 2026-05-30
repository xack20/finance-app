package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.NewLendBorrow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LendBorrowBlockingTest {
    @Test fun `recordBlocking creates lend_borrow + linked txn inside a transaction`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()   // seed "lend"/"borrow"/"transfer" categories (FK targets)
        val accounts = AccountRepository(db)
        val persons = PersonRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val lendBorrow = LendBorrowRepository(db, txns)
        val cash = accounts.add("Cash", AccountKind.CASH, null)
        val karim = persons.addManual("Karim")
        db.transaction {
            lendBorrow.recordBlocking(
                NewLendBorrow(personId = karim, amount = 2000.0, direction = LendBorrowDirection.LENT,
                    accountId = cash, purpose = "loan", ts = 5L, dueDate = null),
            )
        }
        val person = persons.observeAll().first().single { it.person.id == karim }
        assertEquals(2000.0, person.balance)
    }

    @Test fun `findByNameBlocking matches case-insensitively`() = runTest {
        val db = TestDatabase.create()
        val persons = PersonRepository(db)
        val karim = persons.addManual("Karim")
        assertEquals(karim, persons.findByNameBlocking("karim"))
        assertEquals(null, persons.findByNameBlocking("nobody"))
    }
}
