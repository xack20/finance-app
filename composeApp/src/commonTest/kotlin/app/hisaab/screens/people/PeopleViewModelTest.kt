package app.hisaab.screens.people

import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.NewLendBorrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private suspend fun bundle(): Bundle {
        val db = TestDatabase.create()
        val cats = CategoryRepository(db); cats.ensureDefaults()
        val accounts = AccountRepository(db)
        val accountId = accounts.add("Cash", AccountKind.CASH, null)
        val merchants = MerchantRepository(db)
        val tags = TagRepository(db)
        val txn = TransactionRepository(db, merchants, tags)
        val persons = PersonRepository(db)
        val lendBorrow = LendBorrowRepository(db, txn)
        return Bundle(persons, lendBorrow, accounts, accountId)
    }

    private data class Bundle(
        val persons: PersonRepository,
        val lendBorrow: LendBorrowRepository,
        val accounts: AccountRepository,
        val accountId: String,
    )

    @Test
    fun `addManualPerson exposes the new person via people flow`() = runTest {
        val b = bundle()
        val vm = PeopleViewModel(b.persons, b.lendBorrow, b.accounts,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.addManualPerson("Karim")
        advanceUntilIdle()
        val list = b.persons.observeAll().first()
        assertEquals(1, list.size)
        assertEquals("Karim", list[0].person.name)
    }

    @Test
    fun `settle updates the person balance`() = runTest {
        val b = bundle()
        val personId = b.persons.addManual("Karim")
        val (lendBorrowId, _) = b.lendBorrow.record(NewLendBorrow(
            personId = personId, amount = 500.0,
            direction = LendBorrowDirection.LENT,
            accountId = b.accountId, purpose = null,
            ts = 1000L, dueDate = null,
        ))
        val vm = PeopleViewModel(b.persons, b.lendBorrow, b.accounts,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.settle(lendBorrowId, 500.0, b.accountId)
        advanceUntilIdle()
        val pwb = b.persons.observeAll().first().first()
        assertEquals(0.0, pwb.balance)
    }
}
