package app.hisaab.screens.entry

import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewSplitTransaction
import app.hisaab.domain.TxnKind
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() { Dispatchers.setMain(dispatcher) }

    @AfterTest
    fun teardown() { Dispatchers.resetMain() }

    private suspend fun buildBundle(): Bundle {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val personRepo = PersonRepository(db)
        val lendBorrowRepo = LendBorrowRepository(db, txnRepo)
        val catRepo = CategoryRepository(db)
        catRepo.ensureDefaults()
        val accountId = accountRepo.add("Cash", AccountKind.CASH, null)
        return Bundle(txnRepo, lendBorrowRepo, personRepo, accountId)
    }

    private data class Bundle(
        val txnRepo: TransactionRepository,
        val lendBorrowRepo: LendBorrowRepository,
        val personRepo: PersonRepository,
        val accountId: String,
    )

    @Test
    fun `initial state has kind EXPENSE and isValid false`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        assertEquals(TxnKind.EXPENSE, vm.state.value.kind)
        assertFalse(vm.state.value.isValid)
    }

    @Test
    fun `isValid true after amount and account set for EXPENSE`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.setAmount("150")
        vm.setAccount(b.accountId)
        assertTrue(vm.state.value.isValid)
    }

    @Test
    fun `isValid false for LEND until person set`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.setKind(TxnKind.LEND)
        vm.setAmount("500")
        vm.setAccount(b.accountId)
        assertFalse(vm.state.value.isValid)  // no person yet

        vm.setNewPerson("Karim", null)
        assertTrue(vm.state.value.isValid)
    }

    @Test
    fun `setKind clearing LEND removes person fields`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.setKind(TxnKind.LEND)
        vm.setNewPerson("Karim", null)
        vm.setDueDate(1000L)
        vm.setKind(TxnKind.EXPENSE)
        assertEquals(null, vm.state.value.newPersonName)
        assertEquals(null, vm.state.value.dueDate)
    }

    @Test
    fun `addTag is idempotent`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.addTag("urgent")
        vm.addTag("urgent")
        assertEquals(listOf("urgent"), vm.state.value.tagNames)
    }

    @Test
    fun `removeTag drops the tag`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.addTag("urgent")
        vm.addTag("food")
        vm.removeTag("urgent")
        assertEquals(listOf("food"), vm.state.value.tagNames)
    }

    @Test
    fun `save EXPENSE writes a txn`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.setAmount("150")
        vm.setAccount(b.accountId)
        vm.setMerchant("Aarong")
        vm.setCategory("food")
        var done = false
        vm.save { done = true }
        advanceUntilIdle()
        assertTrue(done)
        val rows = b.txnRepo.observeRecent(50).first()
        assertEquals(1, rows.size)
        assertEquals(150.0, rows[0].amount)
        assertEquals(TxnKind.EXPENSE, rows[0].kind)
    }

    @Test
    fun `save LEND with new person writes lend_borrow + txn`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.setKind(TxnKind.LEND)
        vm.setAmount("500")
        vm.setAccount(b.accountId)
        vm.setNewPerson("Karim", null)
        var done = false
        vm.save { done = true }
        advanceUntilIdle()
        assertTrue(done)
        val rows = b.txnRepo.observeRecent(50).first()
        assertEquals(1, rows.size)
        assertEquals(TxnKind.LEND, rows[0].kind)
        val open = b.lendBorrowRepo.observeOpen().first()
        assertEquals(1, open.size)
    }

    @Test
    fun `save with splits writes parent + children`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(b.txnRepo, b.lendBorrowRepo, b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()))
        vm.setAmount("1000")
        vm.setAccount(b.accountId)
        vm.setSplits(listOf(
            NewSplitTransaction(amount = 600.0, categoryId = "food", notes = null, kind = TxnKind.EXPENSE),
            NewSplitTransaction(amount = 400.0, categoryId = "transport", notes = null, kind = TxnKind.EXPENSE),
        ))
        var done = false
        vm.save { done = true }
        advanceUntilIdle()
        assertTrue(done)
        // observeRecent returns only top-level (parent) rows.
        val rows = b.txnRepo.observeRecent(50).first()
        assertEquals(1, rows.size)
        assertEquals(1000.0, rows[0].amount)
    }
}
