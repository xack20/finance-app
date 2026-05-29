package app.hisaab.screens.transaction

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.ParsedBy
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailProvenanceTest {

    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `provenance is null for a manual transaction`() = runTest {
        val db = TestDatabase.create()
        val merchant = MerchantRepository(db)
        val txn = TransactionRepository(db, merchant, TagRepository(db))
        val account = AccountRepository(db)
        val inbox = CaptureInboxRepository(db)
        val accountId = account.add("Cash", AccountKind.CASH, null)
        val id = txn.add(
            NewTransaction(accountId, 100.0, "BDT", 1L, null, null, TxnSource.MANUAL, null, TxnKind.EXPENSE),
        )
        val vm = TransactionDetailViewModel(id, txn, account, CategoryRepository(db), merchant, inbox)
        advanceUntilIdle()
        assertNull(vm.provenance.value)
    }

    @Test
    fun `provenance loads the linked candidate for a captured transaction`() = runTest {
        val db = TestDatabase.create()
        val merchant = MerchantRepository(db)
        val txn = TransactionRepository(db, merchant, TagRepository(db))
        val account = AccountRepository(db)
        val inbox = CaptureInboxRepository(db)
        val accountId = account.add("Cash", AccountKind.CASH, null)
        inbox.insertCandidate(
            CandidateTransaction(
                id = "cap1", receivedAt = 1L, channel = CaptureChannel.SMS, sender = "bKash",
                rawBody = "You have received Tk 500 from 017...", dedupHash = "h1",
                status = CaptureStatus.AUTO_POSTED, confidence = 0.93, parsedBy = ParsedBy.CLOUD_CLAUDE,
                model = "claude-x", parseError = null, amount = 500.0, direction = Direction.CREDIT,
                currency = "BDT", balanceAfter = null, refNo = null, proposedAccountId = accountId,
                proposedCategoryId = null, proposedMerchant = "bKash", createdAt = 1L,
            ),
        )
        // Linked at insert time via NewTransaction.captureId (R1: no link method).
        val txnId = txn.add(
            NewTransaction(accountId, 500.0, "BDT", 1L, "bKash", null, TxnSource.SMS, null, TxnKind.INCOME, captureId = "cap1"),
        )
        val vm = TransactionDetailViewModel(txnId, txn, account, CategoryRepository(db), merchant, inbox)
        advanceUntilIdle()
        val prov = vm.provenance.value
        assertEquals(ParsedBy.CLOUD_CLAUDE, prov?.parsedBy)
        assertEquals("claude-x", prov?.model)
        assertEquals(0.93, prov?.confidence)
    }
}
