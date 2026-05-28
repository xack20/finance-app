package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.ParsedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureInboxRepositoryTest {

    private fun candidate(
        id: String = "cand-1",
        dedupHash: String = "hash-1",
        status: CaptureStatus = CaptureStatus.PENDING,
        receivedAt: Long = 1_000L,
    ) = CandidateTransaction(
        id = id,
        receivedAt = receivedAt,
        channel = CaptureChannel.SMS,
        sender = "bKash",
        rawBody = "You have received Tk 500 from 01700000000. TrxID ABC123",
        dedupHash = dedupHash,
        status = status,
        confidence = 0.92,
        parsedBy = ParsedBy.TEMPLATE,
        model = null,
        parseError = null,
        amount = 500.0,
        direction = Direction.CREDIT,
        currency = "BDT",
        balanceAfter = 1500.0,
        refNo = "ABC123",
        proposedAccountId = "acc-1",
        proposedCategoryId = "cat-1",
        proposedMerchant = "bKash",
        createdAt = 2_000L,
    )

    @Test
    fun `insertCandidate then getById round-trips all fields`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate())
        val loaded = repo.getById("cand-1")
        assertNotNull(loaded)
        assertEquals(500.0, loaded.amount)
        assertEquals(Direction.CREDIT, loaded.direction)
        assertEquals(ParsedBy.TEMPLATE, loaded.parsedBy)
        assertEquals(CaptureStatus.PENDING, loaded.status)
        assertEquals("ABC123", loaded.refNo)
        assertEquals(0.92, loaded.confidence)
        assertEquals("bKash", loaded.proposedMerchant)
    }

    @Test
    fun `insertCandidate is a no-op on duplicate dedupHash`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "cand-1", dedupHash = "dup"))
        repo.insertCandidate(candidate(id = "cand-2", dedupHash = "dup"))
        assertNotNull(repo.getById("cand-1"))
        assertNull(repo.getById("cand-2"))
    }

    @Test
    fun `findByDedupHash returns the matching candidate`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(dedupHash = "findme"))
        val found = repo.findByDedupHash("findme")
        assertNotNull(found)
        assertEquals("cand-1", found.id)
        assertNull(repo.findByDedupHash("missing"))
    }

    @Test
    fun `observePending only returns PENDING ordered by receivedAt desc`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a", receivedAt = 100))
        repo.insertCandidate(candidate(id = "b", dedupHash = "b", receivedAt = 300))
        repo.insertCandidate(
            candidate(id = "c", dedupHash = "c", status = CaptureStatus.AUTO_POSTED, receivedAt = 200),
        )
        val pending = repo.observePending().first()
        assertEquals(listOf("b", "a"), pending.map { it.id })
    }

    @Test
    fun `observePendingCount reflects PENDING rows only`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a"))
        repo.insertCandidate(
            candidate(id = "b", dedupHash = "b", status = CaptureStatus.CONFIRMED),
        )
        assertEquals(1L, repo.observePendingCount().first())
    }

    @Test
    fun `observeRecent returns newest first up to limit across all statuses`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a", receivedAt = 100))
        repo.insertCandidate(
            candidate(id = "b", dedupHash = "b", status = CaptureStatus.DISMISSED, receivedAt = 300),
        )
        repo.insertCandidate(candidate(id = "c", dedupHash = "c", receivedAt = 200))
        val recent = repo.observeRecent(2).first()
        assertEquals(listOf("b", "c"), recent.map { it.id })
    }

    @Test
    fun `markAutoPosted markConfirmed markDismissed transition status`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a"))
        repo.markAutoPosted("a")
        assertEquals(CaptureStatus.AUTO_POSTED, repo.getById("a")?.status)
        repo.markConfirmed("a")
        assertEquals(CaptureStatus.CONFIRMED, repo.getById("a")?.status)
        repo.markDismissed("a")
        assertEquals(CaptureStatus.DISMISSED, repo.getById("a")?.status)
    }

    @Test
    fun `purgeRaw blanks the raw body`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a"))
        repo.purgeRaw("a")
        val loaded = repo.getById("a")
        assertNotNull(loaded)
        assertTrue(loaded.rawBody.isEmpty())
    }
}
