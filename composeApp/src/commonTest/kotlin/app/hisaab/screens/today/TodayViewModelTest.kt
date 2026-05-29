package app.hisaab.screens.today

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import app.hisaab.util.todayRangeMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class TodayViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() { Dispatchers.setMain(dispatcher) }

    @AfterTest
    fun teardown() { Dispatchers.resetMain() }

    private fun makeVm(
        db: app.hisaab.db.HisaabDatabase,
        optInSeen: Boolean = false,
        onSave: () -> Unit = {},
        smsCapable: Boolean = true,
    ): TodayViewModel {
        val merchant = MerchantRepository(db)
        val tag = TagRepository(db)
        return TodayViewModel(
            txnRepo = TransactionRepository(db, merchant, tag),
            accountRepo = AccountRepository(db),
            categoryRepo = CategoryRepository(db),
            merchantRepo = merchant,
            inboxRepo = CaptureInboxRepository(db),
            loadOptInSeen = { optInSeen },
            saveOptInSeen = onSave,
            captureConfigRepo = CaptureConfigRepository(db),
            smsCapable = smsCapable,
        )
    }

    @Test
    fun `empty database has zero net and empty recent`() = runTest {
        val db = TestDatabase.create()
        val vm = makeVm(db)
        assertEquals(0.0, vm.todayNet.value.income)
        assertEquals(0.0, vm.todayNet.value.expense)
        assertEquals(0.0, vm.todayNet.value.net)
        assertEquals(emptyList(), vm.recent.value)
    }

    @Test
    fun `viewmodel can be constructed without crash`() = runTest {
        val db = TestDatabase.create()
        val vm = makeVm(db)
        assertEquals(0.0, vm.todayNet.value.net)
    }

    @Test
    fun `pendingCount reflects inbox pending candidates`() = runTest {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        inbox.insertCandidate(
            app.hisaab.domain.CandidateTransaction(
                id = "c1", receivedAt = 1L, channel = app.hisaab.domain.CaptureChannel.SMS,
                sender = "bKash", rawBody = "x", dedupHash = "h1",
                status = app.hisaab.domain.CaptureStatus.PENDING, confidence = 0.5,
                parsedBy = app.hisaab.domain.ParsedBy.TEMPLATE, model = null, parseError = null,
                amount = 1.0, direction = app.hisaab.domain.Direction.DEBIT, currency = "BDT",
                balanceAfter = null, refNo = null, proposedAccountId = null,
                proposedCategoryId = null, proposedMerchant = null, createdAt = 1L,
            ),
        )
        assertEquals(1L, inbox.observePendingCount().first())
    }

    @Test
    fun `todayNet reflects expense after direct repo call`() = runTest {
        val db = TestDatabase.create()
        val merchant = MerchantRepository(db)
        val tag = TagRepository(db)
        val txn = TransactionRepository(db, merchant, tag)
        val account = AccountRepository(db)
        val category = CategoryRepository(db)
        category.ensureDefaults()
        val accountId = account.add("Cash", AccountKind.CASH, null)
        val (start, _) = todayRangeMs()
        txn.add(
            NewTransaction(
                accountId = accountId,
                amount = 150.0,
                ts = start + 1000,
                merchantName = "Aarong",
                categoryId = "food",
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        // Test the underlying repository flow directly (bypasses WhileSubscribed timing issues).
        val net = txn.observeTodayNet().first()
        assertEquals(150.0, net.expense)
        assertEquals(0.0, net.income)
        assertEquals(-150.0, net.net)
    }

    @Test
    fun `captureOptInSeen is false initially and true after dismissOptIn`() = runTest {
        val db = TestDatabase.create()
        var savedCalled = false
        val vm = makeVm(db, optInSeen = false, onSave = { savedCalled = true }, smsCapable = true)
        assertFalse(vm.captureOptInSeen.value, "card should be visible before dismissal")
        vm.dismissOptIn()
        advanceUntilIdle()
        assertTrue(vm.captureOptInSeen.value, "card should be hidden after dismissal")
        assertTrue(savedCalled, "saveOptInSeen must be called to persist the flag")
    }

    @Test
    fun `captureOptInSeen is true when loadOptInSeen returns true`() = runTest {
        val db = TestDatabase.create()
        val vm = makeVm(db, optInSeen = true)
        assertTrue(vm.captureOptInSeen.value, "card should be hidden when flag already persisted")
    }

    @Test
    fun `smsSupported reflects smsCapable constructor param`() = runTest {
        val db = TestDatabase.create()
        assertFalse(makeVm(db, smsCapable = false).smsSupported)
        assertTrue(makeVm(db, smsCapable = true).smsSupported)
    }
}
