package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssignPendingAccountTest {

    private fun row(id: String, sender: String, status: CaptureStatus = CaptureStatus.PENDING) = CandidateTransaction(
        id = id, receivedAt = 0L, channel = CaptureChannel.EMAIL, sender = sender, rawBody = id, dedupHash = id,
        status = status, confidence = 0.6, parsedBy = null, model = null, parseError = null, amount = 10.0,
        direction = null, currency = "BDT", balanceAfter = null, refNo = null, proposedAccountId = null,
        proposedCategoryId = null, proposedMerchant = null, createdAt = 0L,
    )

    @Test
    fun `mapping a sender gives only its waiting items the account`() = runTest {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val account = AccountRepository(db).ensureDefaultCashAccount()
        inbox.insertCandidate(row("a", "alerts@bank.example"))
        inbox.insertCandidate(row("b", "someone-else"))
        inbox.insertCandidate(row("c", "alerts@bank.example", CaptureStatus.DISMISSED))

        val updated = inbox.assignAccountToPending("alerts@bank.example", account)

        assertEquals(listOf("a"), updated.map { it.id })
        assertEquals(account, inbox.getById("a")?.proposedAccountId)
        assertNull(inbox.getById("b")?.proposedAccountId)
        assertNull(inbox.getById("c")?.proposedAccountId)
    }

    @Test
    fun `senders of items already waiting are registered so they can be mapped`() = runTest {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val senders = SenderRepository(db)
        inbox.insertCandidate(row("a", "alerts@bank.example"))
        inbox.insertCandidate(row("b", "alerts@bank.example"))
        inbox.insertCandidate(row("c", "bKash")) // seeded/known senders are left alone
        senders.seedKnownSenders()
        val before = senders.findBySenderId("bKash")

        registerWaitingSenders(inbox, senders)

        val registered = senders.findBySenderId("alerts@bank.example")
        assertEquals(true, registered?.isFinancial)
        assertNull(registered?.accountId)
        assertEquals(before, senders.findBySenderId("bKash"))
    }
}
