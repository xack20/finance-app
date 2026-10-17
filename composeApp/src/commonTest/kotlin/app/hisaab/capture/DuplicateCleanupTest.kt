package app.hisaab.capture

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DuplicateCleanupTest {

    private val minute = 60_000L

    private fun row(
        id: String,
        channel: CaptureChannel,
        at: Long,
        amount: Double = 500.0,
        ref: String? = null,
        status: CaptureStatus = CaptureStatus.PENDING,
    ) = CandidateTransaction(
        id = id, receivedAt = at, channel = channel, sender = "s-$id", rawBody = "b-$id", dedupHash = "h-$id",
        status = status, confidence = 0.9, parsedBy = null, model = null, parseError = null, amount = amount,
        direction = Direction.DEBIT, currency = "BDT", balanceAfter = null, refNo = ref, proposedAccountId = null,
        proposedCategoryId = null, proposedMerchant = null, createdAt = at,
    )

    // ---- pure finder: the same rules as live capture ----

    @Test
    fun `the same TrxID on another channel is a duplicate of the earlier one`() {
        val sms = row("sms", CaptureChannel.SMS, 0, ref = "Z123")
        val email = row("email", CaptureChannel.EMAIL, 120 * minute, ref = " z123")
        assertEquals(listOf(DuplicatePair(keep = sms, duplicate = email)), findDuplicatePairs(listOf(email, sms)))
    }

    @Test
    fun `the same amount on another channel within 10 minutes is a duplicate`() {
        val sms = row("sms", CaptureChannel.SMS, 0)
        val notif = row("notif", CaptureChannel.NOTIFICATION, 5 * minute)
        assertEquals(1, findDuplicatePairs(listOf(sms, notif)).size)
    }

    @Test
    fun `the same channel, different TrxIDs or more than 10 minutes apart are not duplicates`() {
        assertTrue(findDuplicatePairs(listOf(row("a", CaptureChannel.SMS, 0), row("b", CaptureChannel.SMS, minute))).isEmpty())
        assertTrue(
            findDuplicatePairs(
                listOf(row("a", CaptureChannel.SMS, 0, ref = "A1"), row("b", CaptureChannel.EMAIL, minute, ref = "B2")),
            ).isEmpty(),
        )
        assertTrue(
            findDuplicatePairs(listOf(row("a", CaptureChannel.SMS, 0), row("b", CaptureChannel.EMAIL, 20 * minute))).isEmpty(),
        )
    }

    // ---- scan + removal against a real (in-memory) DB ----

    private class Store {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))

        suspend fun posted(c: CandidateTransaction): CandidateTransaction {
            inbox.insertCandidate(c)
            txns.add(
                NewTransaction(
                    accountId = AccountRepository(db).ensureDefaultCashAccount(), amount = c.amount ?: 0.0,
                    ts = c.receivedAt, merchantName = null, categoryId = null, notes = null,
                    kind = TxnKind.EXPENSE, captureId = c.id,
                ),
            )
            return c
        }

        fun txnCount() = db.transactionQueriesQueries.observeRecentTopLevel(100).executeAsList().size
    }

    @Test
    fun `scan dismisses the Review copy of something already in the ledger`() = runTest {
        val s = Store()
        s.posted(row("sms", CaptureChannel.SMS, 0, ref = "Z1", status = CaptureStatus.AUTO_POSTED))
        s.inbox.insertCandidate(row("email", CaptureChannel.EMAIL, minute, ref = "Z1"))

        val report = DuplicateCleanup(s.db, s.inbox).scan()

        assertEquals(1, report.dismissedFromReview)
        assertTrue(report.ledgerDuplicates.isEmpty())
        assertEquals(CaptureStatus.DISMISSED, s.inbox.getById("email")?.status)
        assertEquals("duplicate", s.inbox.getById("email")?.parseError)
        assertEquals(CaptureStatus.AUTO_POSTED, s.inbox.getById("sms")?.status) // the ledger copy stays
    }

    @Test
    fun `scan only lists duplicates that are both in the ledger`() = runTest {
        val s = Store()
        s.posted(row("sms", CaptureChannel.SMS, 0, ref = "Z1", status = CaptureStatus.AUTO_POSTED))
        s.posted(row("email", CaptureChannel.EMAIL, minute, ref = "Z1", status = CaptureStatus.CONFIRMED))

        val report = DuplicateCleanup(s.db, s.inbox).scan()

        assertEquals(listOf("email"), report.ledgerDuplicates.map { it.duplicate.id })
        assertEquals(2, s.txnCount()) // nothing deleted until the user confirms
    }

    @Test
    fun `removing ledger duplicates deletes the later transaction and marks it duplicate`() = runTest {
        val s = Store()
        s.posted(row("sms", CaptureChannel.SMS, 0, ref = "Z1", status = CaptureStatus.AUTO_POSTED))
        s.posted(row("email", CaptureChannel.EMAIL, minute, ref = "Z1", status = CaptureStatus.CONFIRMED))
        val cleanup = DuplicateCleanup(s.db, s.inbox)

        val removed = cleanup.removeLedgerDuplicates(cleanup.scan().ledgerDuplicates)

        assertEquals(1, removed)
        assertEquals(1, s.txnCount())
        assertEquals("duplicate", s.inbox.getById("email")?.parseError)
        assertNotNull(s.inbox.getById("sms"))
        assertNull(s.db.transactionQueriesQueries.findTxnIdsByCaptureId("email").executeAsOneOrNull())
    }
}
