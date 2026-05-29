package app.hisaab.capture

import app.hisaab.data.AccountRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountMatcherTest {

    private fun mapping(
        sender: String,
        bankType: BankType,
        accountId: String?,
    ) = SenderMapping(
        id = "m_$sender",
        senderId = sender,
        displayName = sender,
        bankType = bankType,
        isFinancial = true,
        templateKey = null,
        accountId = accountId,
        createdAt = 1000L,
    )

    @Test
    fun `returns existing mapped account without creating a new one`() = runTest {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val senderRepo = SenderRepository(db)
        val existingId = accountRepo.add("My bKash", app.hisaab.domain.AccountKind.MFS, "bKash")
        val matcher = AccountMatcher(accountRepo, senderRepo, db)

        val resolved = matcher.resolve(mapping("bKash", BankType.BKASH, accountId = existingId), BankType.BKASH)

        assertEquals(existingId, resolved)
        assertEquals(1, accountRepo.observeActive().first().size)
    }

    @Test
    fun `auto-creates MFS account for bKash and persists mapping`() = runTest {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val senderRepo = SenderRepository(db)
        senderRepo.upsert(mapping("bKash", BankType.BKASH, accountId = null))
        val matcher = AccountMatcher(accountRepo, senderRepo, db)

        val resolved = matcher.resolve(senderRepo.findBySenderId("bKash"), BankType.BKASH)

        assertNotNull(resolved)
        val accounts = accountRepo.observeActive().first()
        assertEquals(1, accounts.size)
        assertEquals(app.hisaab.domain.AccountKind.MFS, accounts.first().kind)
        // mapping persisted
        assertEquals(resolved, senderRepo.findBySenderId("bKash")?.accountId)
    }

    @Test
    fun `auto-creates BANK account for a bank sender`() = runTest {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val senderRepo = SenderRepository(db)
        senderRepo.upsert(mapping("BRAC BANK", BankType.BANK, accountId = null))
        val matcher = AccountMatcher(accountRepo, senderRepo, db)

        val resolved = matcher.resolve(senderRepo.findBySenderId("BRAC BANK"), BankType.BANK)

        assertNotNull(resolved)
        assertEquals(app.hisaab.domain.AccountKind.BANK, accountRepo.observeActive().first().first().kind)
    }

    @Test
    fun `null mapping returns null (forces review)`() = runTest {
        val db = TestDatabase.create()
        val matcher = AccountMatcher(AccountRepository(db), SenderRepository(db), db)
        assertNull(matcher.resolve(mapping = null, bankType = BankType.OTHER))
    }

    @Test
    fun `repeated resolve for same unmapped sender creates exactly one account and no duplicates`() = runTest {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val senderRepo = SenderRepository(db)
        senderRepo.upsert(mapping("bKash", BankType.BKASH, accountId = null))
        val matcher = AccountMatcher(accountRepo, senderRepo, db)

        // First resolve: auto-creates account + persists mapping atomically.
        val firstId = matcher.resolve(senderRepo.findBySenderId("bKash"), BankType.BKASH)
        assertNotNull(firstId)

        // Second resolve with the now-mapped sender: must return the same id, NOT create a second account.
        val secondId = matcher.resolve(senderRepo.findBySenderId("bKash"), BankType.BKASH)
        assertEquals(firstId, secondId)

        // Exactly one account must exist — no duplicate.
        assertEquals(1, accountRepo.observeActive().first().size)
    }
}
