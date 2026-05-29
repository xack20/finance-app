package app.hisaab.screens.capture

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.db.HisaabDatabase
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
class ReviewInboxViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun candidate(
        id: String,
        confidence: Double,
        status: CaptureStatus = CaptureStatus.PENDING,
    ) = CandidateTransaction(
        id = id,
        receivedAt = 1_000L,
        channel = CaptureChannel.SMS,
        sender = "bKash",
        rawBody = "You have received Tk 500",
        dedupHash = "hash-$id",
        status = status,
        confidence = confidence,
        parsedBy = ParsedBy.TEMPLATE,
        model = null,
        parseError = null,
        amount = 500.0,
        direction = Direction.CREDIT,
        currency = "BDT",
        balanceAfter = null,
        refNo = null,
        proposedAccountId = null,
        proposedCategoryId = null,
        proposedMerchant = "bKash",
        createdAt = 1_000L,
    )

    /** Mirrors AppContainer.confirmCandidate: atomic { linked add ; markConfirmed }, link via captureId. */
    private fun confirmCandidateFor(db: HisaabDatabase): suspend (String) -> Boolean = confirm@{ candidateId ->
        val inbox = CaptureInboxRepository(db)
        val txnRepo = txnRepo(db)
        val c = inbox.getById(candidateId) ?: return@confirm false
        val accountId = c.proposedAccountId ?: return@confirm false
        val amount = c.amount ?: return@confirm false
        val kind = when (c.direction) {
            Direction.DEBIT -> TxnKind.EXPENSE
            Direction.CREDIT -> TxnKind.INCOME
            null -> return@confirm false
        }
        db.transaction {
            txnRepo.addBlocking(
                NewTransaction(
                    accountId = accountId, amount = amount, currency = c.currency, ts = c.receivedAt,
                    merchantName = c.proposedMerchant, categoryId = c.proposedCategoryId,
                    source = TxnSource.SMS, notes = null, kind = kind, captureId = candidateId,
                ),
            )
            inbox.markConfirmedBlocking(candidateId)
        }
        true
    }

    @Test
    fun `pending candidates surface in the inbox`() = runTest {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        inbox.insertCandidate(candidate("c1", 0.6))
        ReviewInboxViewModel(
            inboxRepo = inbox,
            accountRepo = AccountRepository(db),
            categoryRepo = CategoryRepository(db),
            confirmCandidate = confirmCandidateFor(db),
        )
        advanceUntilIdle()
        val items = inbox.observePending().first()
        assertEquals(1, items.size)
    }

    @Test
    fun `dismiss marks the candidate dismissed`() = runTest {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        inbox.insertCandidate(candidate("c1", 0.6))
        val vm = ReviewInboxViewModel(inbox, AccountRepository(db), CategoryRepository(db), confirmCandidateFor(db))
        vm.dismiss("c1")
        advanceUntilIdle()
        assertEquals(CaptureStatus.DISMISSED, inbox.getById("c1")?.status)
        assertEquals(0, inbox.observePending().first().size)
    }

    @Test
    fun `confirm marks confirmed and posts a linked transaction`() = runTest {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val account = AccountRepository(db)
        val accountId = account.add("Cash", app.hisaab.domain.AccountKind.CASH, null)
        inbox.insertCandidate(candidate("c1", 0.6).copy(proposedAccountId = accountId))
        val txn = txnRepo(db)
        val vm = ReviewInboxViewModel(inbox, account, CategoryRepository(db), confirmCandidateFor(db))
        vm.confirm("c1")
        advanceUntilIdle()
        assertEquals(CaptureStatus.CONFIRMED, inbox.getById("c1")?.status)
        val recent = txn.observeRecent(50).first()
        assertEquals(1, recent.size)
        // Linked at insert time via NewTransaction.captureId (R1: no link method).
        assertEquals("c1", recent.first().captureId)
    }

    @Test
    fun `confirm without a resolved account does not crash and leaves pending`() = runTest {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        inbox.insertCandidate(candidate("c1", 0.6)) // proposedAccountId = null
        val vm = ReviewInboxViewModel(inbox, AccountRepository(db), CategoryRepository(db), confirmCandidateFor(db))
        vm.confirm("c1")
        advanceUntilIdle()
        // No account → confirmCandidate returns false → stays PENDING for the user to Edit.
        assertEquals(CaptureStatus.PENDING, inbox.getById("c1")?.status)
    }

    @Test
    fun `confirmAllHighConfidence confirms only at-or-above threshold candidates`() = runTest {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val account = AccountRepository(db)
        val accountId = account.add("Cash", app.hisaab.domain.AccountKind.CASH, null)
        inbox.insertCandidate(candidate("hi", 0.95).copy(proposedAccountId = accountId))
        inbox.insertCandidate(candidate("lo", 0.40).copy(proposedAccountId = accountId))
        val vm = ReviewInboxViewModel(inbox, account, CategoryRepository(db), confirmCandidateFor(db))
        vm.confirmAllHighConfidence(threshold = 0.85)
        advanceUntilIdle()
        assertEquals(CaptureStatus.CONFIRMED, inbox.getById("hi")?.status)
        assertEquals(CaptureStatus.PENDING, inbox.getById("lo")?.status)
        assertTrue(inbox.observePending().first().any { it.id == "lo" })
    }

    private fun txnRepo(db: HisaabDatabase): TransactionRepository =
        TransactionRepository(db, MerchantRepository(db), TagRepository(db))
}
