package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantRepositoryTest {

    @Test
    fun `upsertByName dedups case-insensitive with normalized whitespace`() = runTest {
        val repo = MerchantRepository(TestDatabase.create())
        val a = repo.upsertByName("Aarong")
        val b = repo.upsertByName("AARONG")
        val c = repo.upsertByName("  aarong  ")
        assertEquals(a, b)
        assertEquals(a, c)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `different merchants get different ids`() = runTest {
        val repo = MerchantRepository(TestDatabase.create())
        val a = repo.upsertByName("Aarong")
        val b = repo.upsertByName("Daraz")
        assertTrue(a != b)
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `searchByPrefix returns merchants matching normalized prefix`() = runTest {
        val repo = MerchantRepository(TestDatabase.create())
        repo.upsertByName("Aarong")
        repo.upsertByName("Daraz")
        repo.upsertByName("Anwar Bakery")
        val matches = repo.searchByPrefix("aa").first()
        assertEquals(1, matches.size)
        assertEquals("Aarong", matches[0].name)
    }

    @Test
    fun `searchByPrefix returns empty for no matches`() = runTest {
        val repo = MerchantRepository(TestDatabase.create())
        repo.upsertByName("Aarong")
        assertEquals(0, repo.searchByPrefix("xyz").first().size)
    }
}
