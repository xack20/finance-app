package app.hisaab.capture.agent

import app.hisaab.data.AccountRepository
import app.hisaab.data.AgentMemoryRepository
import app.hisaab.data.AgentQuestionRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CaptureCommitterTest {

    private fun msg(id: String, at: Long, channel: CaptureChannel = CaptureChannel.SMS) = CandidateTransaction(
        id = id, receivedAt = at, channel = channel, sender = "bKash", rawBody = "b-$id", dedupHash = "h-$id",
        status = CaptureStatus.QUEUED, confidence = null, parsedBy = null, model = null, parseError = null,
        amount = null, direction = null, currency = "BDT", balanceAfter = null, refNo = null, proposedAccountId = null,
        proposedCategoryId = null, proposedMerchant = null, createdAt = at,
    )

    private class Store {
        val db = TestDatabase.create()
        val inbox = CaptureInboxRepository(db)
        val accounts = AccountRepository(db)
        val categories = CategoryRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val questions = AgentQuestionRepository(db)
        val memory = AgentMemoryRepository(db)
        val committer = CaptureCommitter(
            db, accounts, categories, txns, PersonRepository(db), LendBorrowRepository(db, txns), questions, memory,
        )

        suspend fun queue(vararg messages: CandidateTransaction): List<CandidateTransaction> {
            categories.ensureDefaults()
            messages.forEach { inbox.insertCandidate(it) }
            return messages.toList()
        }

        suspend fun ledger() = txns.observeRecent(50).first()
        suspend fun status(id: String) = inbox.getById(id)?.status
    }

    @Test
    fun `a post adds one transaction linked to its message at the message's time`() = runTest {
        val s = Store()
        s.accounts.add("bKash", AccountKind.MFS, institution = null)
        val batch = s.queue(msg("m1", at = 5_000))

        val events = s.committer.commit(
            batch,
            listOf(CaptureDecision.PostTransaction(listOf("m1"), "BKASH", 500.0, TxnKind.EXPENSE, "food", "Foodpanda")),
        )

        val txn = s.ledger().single()
        assertEquals(500.0, txn.amount)
        assertEquals(5_000, txn.ts)
        assertEquals("m1", txn.captureId)
        assertEquals("food", txn.categoryId)
        assertEquals(CaptureStatus.AUTO_POSTED, s.status("m1"))
        assertEquals(Direction.DEBIT, events.single().direction)
    }

    @Test
    fun `copies of a posted payment are dismissed and the earliest is linked`() = runTest {
        val s = Store()
        s.accounts.add("City Bank", AccountKind.BANK, institution = null)
        val batch = s.queue(msg("email", at = 2_000, CaptureChannel.EMAIL), msg("sms", at = 1_000))

        s.committer.commit(
            batch,
            listOf(CaptureDecision.PostTransaction(listOf("email", "sms"), "City Bank", 1200.0, TxnKind.EXPENSE)),
        )

        assertEquals("sms", s.ledger().single().captureId)
        assertEquals(CaptureStatus.DISMISSED, s.status("email"))
        assertTrue("sms" in s.inbox.getById("email")?.parseError.orEmpty())
    }

    @Test
    fun `a transfer moves money between own accounts`() = runTest {
        val s = Store()
        s.accounts.add("City Bank", AccountKind.BANK, institution = null)
        s.accounts.add("bKash", AccountKind.MFS, institution = null)
        val batch = s.queue(msg("m1", at = 1_000))

        s.committer.commit(batch, listOf(CaptureDecision.PostTransfer(listOf("m1"), "City Bank", "bKash", 3000.0)))

        val ids = s.accounts.observeActive().first().associate { it.name to it.id }
        val legs = s.ledger().filter { it.kind == TxnKind.TRANSFER }
        assertEquals(setOf(ids["City Bank"], ids["bKash"]), legs.map { it.accountId }.toSet())
        assertEquals("m1", legs.single { it.accountId == ids["City Bank"] }.captureId) // the outgoing leg
        assertEquals(CaptureStatus.AUTO_POSTED, s.status("m1"))
    }

    @Test
    fun `lending to a person is recorded`() = runTest {
        val s = Store()
        s.accounts.add("bKash", AccountKind.MFS, institution = null)
        val batch = s.queue(msg("m1", at = 1_000))

        s.committer.commit(
            batch,
            listOf(CaptureDecision.PostLendBorrow(listOf("m1"), "Rahim", 500.0, LendBorrowDirection.LENT, "bKash")),
        )

        assertEquals(TxnKind.LEND, s.ledger().single().kind)
        assertEquals(CaptureStatus.AUTO_POSTED, s.status("m1"))
    }

    @Test
    fun `ask creates a question and parks the messages, skip and remember are kept`() = runTest {
        val s = Store()
        val batch = s.queue(msg("m1", at = 1_000), msg("m2", at = 2_000))

        s.committer.commit(
            batch,
            listOf(
                CaptureDecision.Ask(listOf("m1"), "Is this your card?", listOf("Yes", "No")),
                CaptureDecision.Skip(listOf("m2"), "promo"),
                CaptureDecision.Remember("16216 is bKash"),
            ),
        )

        assertEquals("Is this your card?", s.questions.observeOpen().first().single().question)
        assertEquals(CaptureStatus.PENDING, s.status("m1"))
        assertEquals(CaptureStatus.DISMISSED, s.status("m2"))
        assertEquals("promo", s.inbox.getById("m2")?.parseError)
        assertEquals(listOf("16216 is bKash"), s.memory.all().map { it.fact })
    }

    @Test
    fun `a new account can be created and used in the same batch`() = runTest {
        val s = Store()
        val batch = s.queue(msg("m1", at = 1_000))

        s.committer.commit(
            batch,
            listOf(
                CaptureDecision.CreateAccount("Nagad", AccountKind.MFS, institution = "Nagad"),
                CaptureDecision.PostTransaction(listOf("m1"), "nagad", 50.0, TxnKind.INCOME),
            ),
        )

        val nagad = s.accounts.observeActive().first().single { it.name == "Nagad" }
        assertEquals(nagad.id, s.ledger().single().accountId)
    }

    @Test
    fun `a failing write rolls back the whole batch`() = runTest {
        val s = Store()
        val batch = s.queue(msg("m1", at = 1_000), msg("m2", at = 2_000))

        assertFailsWith<IllegalArgumentException> {
            s.committer.commit(
                batch,
                listOf(
                    CaptureDecision.Skip(listOf("m2"), "promo"),
                    CaptureDecision.Remember("a fact"),
                    CaptureDecision.PostTransaction(listOf("m1"), "Ghost", 50.0, TxnKind.EXPENSE),
                ),
            )
        }

        assertEquals(CaptureStatus.QUEUED, s.status("m2"))
        assertTrue(s.memory.all().isEmpty())
        assertTrue(s.ledger().isEmpty())
    }

    @Test
    fun `an account that already exists is reused, not created twice`() = runTest {
        val s = Store()
        s.accounts.add("bKash", AccountKind.MFS, institution = null)
        val batch = s.queue(msg("m1", at = 1_000))

        s.committer.commit(
            batch,
            listOf(
                CaptureDecision.CreateAccount("BKASH", AccountKind.MFS, null),
                CaptureDecision.CreateAccount("bkash", AccountKind.MFS, null),
                CaptureDecision.PostTransaction(listOf("m1"), "bKash", 50.0, TxnKind.EXPENSE),
            ),
        )

        assertEquals(1, s.accounts.observeActive().first().count { it.name.equals("bkash", ignoreCase = true) })
    }

    @Test
    fun `the merchant is saved with the payment`() = runTest {
        val s = Store()
        s.accounts.add("bKash", AccountKind.MFS, institution = null)
        val batch = s.queue(msg("m1", at = 1_000))

        s.committer.commit(
            batch,
            listOf(CaptureDecision.PostTransaction(listOf("m1"), "bKash", 120.0, TxnKind.EXPENSE, merchant = "Foodpanda")),
        )

        assertTrue(s.ledger().single().merchantId != null)
    }
}

