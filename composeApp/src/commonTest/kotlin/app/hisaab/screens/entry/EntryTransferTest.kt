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
import kotlin.test.assertNotNull

/**
 * T11: Verifies that the manual transfer path posts paired TRANSFER legs via transferBlocking.
 *
 * Test approach: both approaches used —
 *   (a) TransactionRepository.transfer directly — proves 2 paired legs with shared transfer_group_id.
 *   (b) EntryViewModel.save with kind=TRANSFER — proves VM wiring end-to-end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryTransferTest {

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
        val accountA = accountRepo.add("Cash", AccountKind.CASH, null)
        val accountB = accountRepo.add("Bank", AccountKind.BANK, null)
        return Bundle(txnRepo, lendBorrowRepo, personRepo, accountA, accountB)
    }

    private data class Bundle(
        val txnRepo: TransactionRepository,
        val lendBorrowRepo: LendBorrowRepository,
        val personRepo: PersonRepository,
        val accountA: String,
        val accountB: String,
    )

    // (a) Repository-level: transfer() inserts exactly 2 TRANSFER legs sharing the same group id
    @Test
    fun `transfer() inserts two TRANSFER legs sharing transfer_group_id`() = runTest {
        val b = buildBundle()
        val groupId = b.txnRepo.transfer(
            fromAccountId = b.accountA,
            toAccountId = b.accountB,
            amount = 500.0,
            ts = 1_000_000L,
            notes = "test transfer",
        )
        assertNotNull(groupId)

        val rows = b.txnRepo.observeRecent(50).first()
        assertEquals(2, rows.size, "Expected exactly 2 TRANSFER legs")

        val kinds = rows.map { it.kind }
        assertEquals(listOf(TxnKind.TRANSFER, TxnKind.TRANSFER), kinds)

        val groupIds = rows.map { it.transferGroupId }.toSet()
        assertEquals(1, groupIds.size, "Both legs must share the same transfer_group_id")
        assertEquals(groupId, groupIds.first())

        val accountIds = rows.map { it.accountId }.toSet()
        assertEquals(setOf(b.accountA, b.accountB), accountIds, "One leg per account")
    }

    // (b) VM-level: EntryViewModel.save with TRANSFER kind produces 2 paired legs
    @Test
    fun `save TRANSFER via VM posts two paired TRANSFER legs`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(
            txnRepo = b.txnRepo,
            lendBorrowRepo = b.lendBorrowRepo,
            personRepo = b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()),
        )

        vm.setKind(TxnKind.TRANSFER)
        vm.setAmount("1000")
        vm.setAccount(b.accountA)
        vm.setToAccount(b.accountB)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertEquals(true, done, "onDone callback must be invoked")

        val rows = b.txnRepo.observeRecent(50).first()
        assertEquals(2, rows.size, "Expected exactly 2 TRANSFER legs from VM save")

        val groupIds = rows.map { it.transferGroupId }.toSet()
        assertEquals(1, groupIds.size, "Both legs must share the same transfer_group_id")

        val accountIds = rows.map { it.accountId }.toSet()
        assertEquals(setOf(b.accountA, b.accountB), accountIds, "One leg per account")
    }

    // isValid gate: TRANSFER without toAccountId should block save
    @Test
    fun `isValid false for TRANSFER when toAccountId not set`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(
            txnRepo = b.txnRepo,
            lendBorrowRepo = b.lendBorrowRepo,
            personRepo = b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()),
        )
        vm.setKind(TxnKind.TRANSFER)
        vm.setAmount("100")
        vm.setAccount(b.accountA)
        // toAccountId not set
        assertEquals(false, vm.state.value.isValid)
    }

    // isValid gate: TRANSFER from == to should block save
    @Test
    fun `isValid false for TRANSFER when from and to are the same account`() = runTest {
        val b = buildBundle()
        val vm = EntryViewModel(
            txnRepo = b.txnRepo,
            lendBorrowRepo = b.lendBorrowRepo,
            personRepo = b.personRepo,
            scope = CoroutineScope(dispatcher + SupervisorJob()),
        )
        vm.setKind(TxnKind.TRANSFER)
        vm.setAmount("100")
        vm.setAccount(b.accountA)
        vm.setToAccount(b.accountA)  // same as from
        assertEquals(false, vm.state.value.isValid)
    }
}
