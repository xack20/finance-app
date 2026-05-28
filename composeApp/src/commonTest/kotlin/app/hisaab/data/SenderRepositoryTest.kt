package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SenderRepositoryTest {

    private fun mapping(
        id: String = "s-1",
        senderId: String = "bKash",
        bankType: BankType = BankType.BKASH,
        accountId: String? = null,
    ) = SenderMapping(
        id = id,
        senderId = senderId,
        displayName = "bKash",
        bankType = bankType,
        isFinancial = true,
        templateKey = "bkash",
        accountId = accountId,
        createdAt = 1_000L,
    )

    @Test
    fun `upsert then findBySenderId round-trips`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.upsert(mapping())
        val found = repo.findBySenderId("bKash")
        assertNotNull(found)
        assertEquals(BankType.BKASH, found.bankType)
        assertEquals("bkash", found.templateKey)
        assertTrue(found.isFinancial)
        assertNull(repo.findBySenderId("UNKNOWN"))
    }

    @Test
    fun `upsert on existing senderId updates the row`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.upsert(mapping(id = "s-1", senderId = "NAGAD", bankType = BankType.NAGAD))
        repo.upsert(mapping(id = "s-2", senderId = "NAGAD", bankType = BankType.OTHER))
        val all = repo.observeAll().first().filter { it.senderId == "NAGAD" }
        assertEquals(1, all.size)
        assertEquals(BankType.OTHER, all[0].bankType)
    }

    @Test
    fun `setAccount maps an account to a sender`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.upsert(mapping(senderId = "bKash"))
        repo.setAccount("bKash", "acc-99")
        assertEquals("acc-99", repo.findBySenderId("bKash")?.accountId)
    }

    @Test
    fun `seedKnownSenders inserts bKash Nagad Rocket and three banks`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.seedKnownSenders()
        val all = repo.observeAll().first()
        val ids = all.map { it.senderId }.toSet()
        assertTrue("bKash" in ids)
        assertTrue("NAGAD" in ids)
        assertTrue("Rocket" in ids)
        assertTrue(ids.any { it.contains("CITY", ignoreCase = true) })
        assertTrue(ids.any { it.contains("BRAC", ignoreCase = true) })
        assertTrue(ids.any { it.contains("DBBL", ignoreCase = true) })
        assertEquals(BankType.BKASH, repo.findBySenderId("bKash")?.bankType)
        assertEquals(BankType.NAGAD, repo.findBySenderId("NAGAD")?.bankType)
        assertEquals(BankType.ROCKET, repo.findBySenderId("Rocket")?.bankType)
    }

    @Test
    fun `seedKnownSenders is idempotent and preserves user account mapping`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.seedKnownSenders()
        repo.setAccount("bKash", "acc-user")
        repo.seedKnownSenders()
        val all = repo.observeAll().first()
        assertEquals(all.size, all.map { it.senderId }.toSet().size) // no duplicate senderIds
        assertEquals("acc-user", repo.findBySenderId("bKash")?.accountId)
    }
}
