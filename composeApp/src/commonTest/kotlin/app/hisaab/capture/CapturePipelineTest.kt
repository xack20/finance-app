package app.hisaab.capture

import app.hisaab.capture.support.FakeLlmProvider
import app.hisaab.capture.support.FakeLlmRouter
import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.RawCapture
import app.hisaab.domain.SenderMapping
import app.hisaab.llm.LlmParseResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapturePipelineTest {

    private class Fixture(
        val db: HisaabDatabase,
        val inboxRepo: CaptureInboxRepository,
        val senderRepo: SenderRepository,
        val accountRepo: AccountRepository,
        val categoryRepo: CategoryRepository,
        val merchantRepo: MerchantRepository,
        val txnRepo: TransactionRepository,
        val configRepo: CaptureConfigRepository,
        val events: MutableSharedFlow<CaptureEvent>,
    )

    private suspend fun fixture(): Fixture {
        val db = TestDatabase.create()
        val categoryRepo = CategoryRepository(db)
        categoryRepo.ensureDefaults()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        return Fixture(
            db = db,
            inboxRepo = CaptureInboxRepository(db),
            senderRepo = SenderRepository(db),
            accountRepo = AccountRepository(db),
            categoryRepo = categoryRepo,
            merchantRepo = merchantRepo,
            txnRepo = TransactionRepository(db, merchantRepo, tagRepo),
            configRepo = CaptureConfigRepository(db),
            // replay=0: matches production AppContainer; tests collect via a real subscriber.
            events = MutableSharedFlow(extraBufferCapacity = 16),
        )
    }

    private fun pipeline(
        f: Fixture,
        router: FakeLlmRouter,
    ): CapturePipeline = CapturePipeline(
        db = f.db,
        inboxRepo = f.inboxRepo,
        senderRepo = f.senderRepo,
        accountMatcher = AccountMatcher(f.accountRepo, f.senderRepo, f.db),
        preFilter = SmsPreFilter(f.senderRepo),
        llmRouter = router,
        txnRepo = f.txnRepo,
        configRepo = f.configRepo,
        captureEvents = f.events,
    )

    @Test
    fun `a rejected LLM key is reported with the capture's channel`() = runTest {
        val f = fixture()
        val rejecting = object : app.hisaab.llm.LlmProvider {
            override val id = app.hisaab.llm.ProviderId.CLOUD_CLAUDE
            override suspend fun isAvailable() = true
            override suspend fun parse(req: app.hisaab.llm.ParseRequest): app.hisaab.llm.LlmParseResult =
                throw app.hisaab.llm.LlmException(app.hisaab.llm.LlmError.InvalidKey)
            override suspend fun categorize(merchant: String, categories: List<app.hisaab.domain.Category>) = null
        }
        val reported = mutableListOf<Pair<CaptureChannel, app.hisaab.llm.LlmError>>()
        CapturePipeline(
            db = f.db, inboxRepo = f.inboxRepo, senderRepo = f.senderRepo,
            accountMatcher = AccountMatcher(f.accountRepo, f.senderRepo, f.db), preFilter = SmsPreFilter(f.senderRepo),
            llmRouter = FakeLlmRouter(rejecting), txnRepo = f.txnRepo, configRepo = f.configRepo,
            captureEvents = f.events, onLlmError = { raw, error -> reported += raw.channel to error },
        ).process(raw("NEWBANK", "Your A/C debited BDT 50.00 TxnID K1"))
        // The capture still lands (PENDING, llm_failed); the key problem must reach the sync status.
        assertEquals<List<Pair<CaptureChannel, app.hisaab.llm.LlmError>>>(listOf(CaptureChannel.SMS to app.hisaab.llm.LlmError.InvalidKey), reported)
    }

    @Test
    fun `a successful LLM parse is reported so a stale key error can clear`() = runTest {
        val f = fixture()
        val provider = FakeLlmProvider(
            result = app.hisaab.llm.LlmParseResult(
                amount = 50.0, direction = app.hisaab.domain.Direction.DEBIT, merchant = "X", categoryId = null,
                balanceAfter = null, refNo = "K2", confidence = 0.9, isFinancial = true,
            ),
        )
        var successes = 0
        CapturePipeline(
            db = f.db, inboxRepo = f.inboxRepo, senderRepo = f.senderRepo,
            accountMatcher = AccountMatcher(f.accountRepo, f.senderRepo, f.db), preFilter = SmsPreFilter(f.senderRepo),
            llmRouter = FakeLlmRouter(provider), txnRepo = f.txnRepo, configRepo = f.configRepo,
            captureEvents = f.events, onLlmSuccess = { successes++ },
        ).process(raw("NEWBANK", "Your A/C debited BDT 50.00 TxnID K2"))
        assertEquals(1, successes)
    }

    // ---- cross-channel duplicates (same payment by SMS + email + notification) ----

    private val minute = 60_000L

    private fun capture(channel: CaptureChannel, sender: String, body: String, ts: Long) =
        RawCapture(sender = sender, body = body, receivedAt = ts, channel = channel)

    private suspend fun ingest(f: Fixture, raw: RawCapture, amount: Double, ref: String?) =
        pipeline(
            f,
            FakeLlmRouter(
                FakeLlmProvider(
                    result = LlmParseResult(
                        amount = amount, direction = Direction.DEBIT, merchant = "M", categoryId = null,
                        balanceAfter = null, refNo = ref, confidence = 0.9, isFinancial = true,
                    ),
                ),
            ),
        ).process(raw)

    private suspend fun duplicatesRecorded(f: Fixture) =
        f.inboxRepo.observeRecent(20).first().count { it.status == CaptureStatus.DISMISSED && it.parseError == "duplicate" }

    @Test
    fun `the same TrxID arriving by email after the SMS is skipped`() = runTest {
        val f = fixture()
        ingest(f, capture(CaptureChannel.SMS, "NEWBANK", "debited BDT 999.00 TxnID Z123", 1_000L), 999.0, "Z123")
        // Hours later, different sender and wording — the transaction ID alone proves it's the same payment.
        ingest(f, capture(CaptureChannel.EMAIL, "alerts@newbank.com", "Debited BDT 999.00, TrxID z123", 1_000L + 120 * minute), 999.0, " z123 ")
        assertEquals(1, f.inboxRepo.getPending().size)
        assertEquals(1, duplicatesRecorded(f)) // recorded so a catch-up re-read doesn't parse it again
    }

    @Test
    fun `the same amount from another channel within 10 minutes is skipped`() = runTest {
        val f = fixture()
        ingest(f, capture(CaptureChannel.SMS, "NEWBANK", "debited BDT 500.00", 1_000L), 500.0, null)
        ingest(f, capture(CaptureChannel.NOTIFICATION, "Bank app", "Payment of BDT 500.00 made", 1_000L + 5 * minute), 500.0, null)
        assertEquals(1, f.inboxRepo.getPending().size)
    }

    @Test
    fun `two same-amount payments on the same channel are both kept`() = runTest {
        val f = fixture()
        ingest(f, capture(CaptureChannel.SMS, "NEWBANK", "debited BDT 500.00 at shop A", 1_000L), 500.0, null)
        ingest(f, capture(CaptureChannel.SMS, "NEWBANK", "debited BDT 500.00 at shop B", 1_000L + 3 * minute), 500.0, null)
        assertEquals(2, f.inboxRepo.getPending().size)
    }

    @Test
    fun `different TrxIDs are kept even with the same amount and time`() = runTest {
        val f = fixture()
        ingest(f, capture(CaptureChannel.SMS, "NEWBANK", "debited BDT 500.00 TxnID A1", 1_000L), 500.0, "A1")
        ingest(f, capture(CaptureChannel.EMAIL, "alerts@newbank.com", "Debited BDT 500.00 TrxID B2", 1_000L + minute), 500.0, "B2")
        assertEquals(2, f.inboxRepo.getPending().size)
    }

    @Test
    fun `the same amount more than 10 minutes apart is kept`() = runTest {
        val f = fixture()
        ingest(f, capture(CaptureChannel.SMS, "NEWBANK", "debited BDT 500.00", 1_000L), 500.0, null)
        ingest(f, capture(CaptureChannel.EMAIL, "alerts@newbank.com", "Debited BDT 500.00", 1_000L + 20 * minute), 500.0, null)
        assertEquals(2, f.inboxRepo.getPending().size)
    }

    private fun raw(sender: String, body: String, ts: Long = 1000L) =
        RawCapture(sender = sender, body = body, receivedAt = ts, channel = CaptureChannel.SMS)

    private suspend fun seedSender(
        f: Fixture,
        sender: String,
        bankType: BankType,
        templateKey: String?,
        accountId: String? = null,
        isFinancial: Boolean = true,
    ) {
        f.senderRepo.upsert(
            SenderMapping(
                id = "m_$sender",
                senderId = sender,
                displayName = sender,
                bankType = bankType,
                isFinancial = isFinancial,
                templateKey = templateKey,
                accountId = accountId,
                createdAt = 1000L,
            ),
        )
    }

    @Test
    fun `known-template high-confidence message auto-posts atomically, links capture_id, and emits AutoPosted`() =
        runTest(UnconfinedTestDispatcher()) {
            val f = fixture()
            seedSender(f, "bKash", BankType.BKASH, templateKey = "bkash")
            val sample = SmsCorpus.byName("bkash_received_money")

            // Subscribe BEFORE processing so we don't miss the emission (replay=0).
            val received = mutableListOf<CaptureEvent>()
            val collectJob = launch { f.events.collect { received += it } }

            pipeline(f, FakeLlmRouter(null)).process(raw(sample.sender, sample.body))

            val candidate = f.inboxRepo.observeRecent(10).first().single()
            assertEquals(CaptureStatus.AUTO_POSTED, candidate.status)
            assertEquals(1500.0, candidate.amount)
            assertEquals(Direction.CREDIT, candidate.direction)
            assertNotNull(candidate.proposedAccountId)

            // (iii) atomic auto-post: exactly one ledger txn now exists alongside the AUTO_POSTED candidate.
            val txns = f.txnRepo.observeRecent(10).first()
            assertEquals(1, txns.size)
            // (ii) the txn was created ALREADY LINKED to its candidate (NewTransaction.captureId).
            assertEquals(candidate.id, txns.single().captureId)

            // (i) AutoPosted event emitted into the injected SharedFlow.
            assertEquals(1, received.size)
            val event = received.single()
            assertTrue(event is CaptureEvent.AutoPosted)
            val posted = event as CaptureEvent.AutoPosted
            assertEquals(candidate.id, posted.candidateId)
            assertEquals(txns.single().id, posted.txnId)
            assertEquals(1500.0, posted.amount)
            assertEquals("bKash", posted.sender)
            assertEquals(Direction.CREDIT, posted.direction)

            collectJob.cancel()
        }

    @Test
    fun `unknown financial message routes to the LLM`() = runTest {
        val f = fixture()
        seedSender(f, "NEWBANK", BankType.BANK, templateKey = null)
        val provider = FakeLlmProvider(
            result = LlmParseResult(
                amount = 999.0,
                direction = Direction.DEBIT,
                merchant = "GROCERY",
                categoryId = "food",
                balanceAfter = 5000.0,
                refNo = "Z123",
                confidence = 0.95,
                isFinancial = true,
            ),
        )
        val router = FakeLlmRouter(provider)

        pipeline(f, router).process(raw("NEWBANK", "Your A/C debited BDT 999.00 TxnID Z123 at GROCERY"))

        assertEquals(1, router.activeCallCount)
        assertNotNull(provider.lastRequest)
        val candidate = f.inboxRepo.observeRecent(10).first().single()
        assertEquals(999.0, candidate.amount)
        // LLM-only unknown is capped <= 0.7 -> below 0.85 threshold -> PENDING
        assertEquals(CaptureStatus.PENDING, candidate.status)
        // No auto-post -> no event emitted (no subscribers; buffer stays empty).
        assertEquals(0, f.events.subscriptionCount.value)
    }

    @Test
    fun `no LLM and incomplete template saves PENDING with parse_error`() = runTest {
        val f = fixture()
        seedSender(f, "NEWBANK", BankType.BANK, templateKey = null)

        pipeline(f, FakeLlmRouter(null)).process(raw("NEWBANK", "Your A/C debited BDT 50.00 TxnID Q1"))

        val candidate = f.inboxRepo.observeRecent(10).first().single()
        assertEquals(CaptureStatus.PENDING, candidate.status)
        assertEquals("needs_manual", candidate.parseError)
    }

    @Test
    fun `duplicate message is skipped`() = runTest {
        val f = fixture()
        seedSender(f, "bKash", BankType.BKASH, templateKey = "bkash")
        val sample = SmsCorpus.byName("bkash_payment")
        val p = pipeline(f, FakeLlmRouter(null))

        p.process(raw(sample.sender, sample.body))
        p.process(raw(sample.sender, sample.body)) // exact duplicate

        assertEquals(1, f.inboxRepo.observeRecent(10).first().size)
    }

    @Test
    fun `low-confidence parse routes to PENDING`() = runTest {
        val f = fixture()
        seedSender(f, "NEWBANK", BankType.BANK, templateKey = null)
        val provider = FakeLlmProvider(
            result = LlmParseResult(
                amount = 200.0,
                direction = Direction.DEBIT,
                merchant = null,
                categoryId = null,
                balanceAfter = null,
                refNo = null,
                confidence = 0.4,
                isFinancial = true,
            ),
        )
        pipeline(f, FakeLlmRouter(provider)).process(raw("NEWBANK", "debited BDT 200.00 TxnID L1"))

        assertEquals(CaptureStatus.PENDING, f.inboxRepo.observeRecent(10).first().single().status)
    }

    @Test
    fun `alwaysReview forces PENDING even for a high-confidence template`() =
        runTest(UnconfinedTestDispatcher()) {
            val f = fixture()
            f.configRepo.setAlwaysReview(true)
            seedSender(f, "bKash", BankType.BKASH, templateKey = "bkash")
            val sample = SmsCorpus.byName("bkash_received_money")

            // Subscribe before processing to catch any stray emission.
            val received = mutableListOf<CaptureEvent>()
            val collectJob = launch { f.events.collect { received += it } }

            pipeline(f, FakeLlmRouter(null)).process(raw(sample.sender, sample.body))

            val candidate = f.inboxRepo.observeRecent(10).first().single()
            assertEquals(CaptureStatus.PENDING, candidate.status)
            assertEquals(0, f.txnRepo.observeRecent(10).first().size)
            assertTrue(received.isEmpty())

            collectJob.cancel()
        }

    @Test
    fun `non-financial promo sender is dropped entirely`() = runTest {
        val f = fixture()
        seedSender(f, "ROBI", BankType.OTHER, templateKey = null, isFinancial = false)
        val sample = SmsCorpus.byName("robi_promo_nonfinancial")

        pipeline(f, FakeLlmRouter(null)).process(raw(sample.sender, sample.body))

        assertEquals(0, f.inboxRepo.observeRecent(10).first().size)
    }
}
