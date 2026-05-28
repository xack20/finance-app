package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountRepositoryTest {

    @Test
    fun `ensureDefaultCashAccount is idempotent`() = runTest {
        val repo = AccountRepository(TestDatabase.create())
        val firstId = repo.ensureDefaultCashAccount()
        val secondId = repo.ensureDefaultCashAccount()
        assertEquals(firstId, secondId)
        assertEquals(1, repo.observeActive().first().size)
    }

    @Test
    fun `add then observeActive returns the new account`() = runTest {
        val repo = AccountRepository(TestDatabase.create())
        val id = repo.add(name = "bKash", kind = AccountKind.MFS, institution = "bKash")
        val accounts = repo.observeActive().first()
        assertEquals(1, accounts.size)
        assertEquals(id, accounts[0].id)
        assertEquals(AccountKind.MFS, accounts[0].kind)
        assertEquals("BDT", accounts[0].currency)
    }

    @Test
    fun `archive removes from observeActive`() = runTest {
        val repo = AccountRepository(TestDatabase.create())
        val id = repo.add("Cash", AccountKind.CASH, null)
        repo.archive(id)
        assertEquals(0, repo.observeActive().first().size)
    }

    @Test
    fun `rename updates the name visible in observeActive`() = runTest {
        val repo = AccountRepository(TestDatabase.create())
        val id = repo.add("Cash", AccountKind.CASH, null)
        repo.rename(id, "Wallet")
        assertEquals("Wallet", repo.observeActive().first()[0].name)
    }
}
