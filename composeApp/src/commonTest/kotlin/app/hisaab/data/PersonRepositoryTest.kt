package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class PersonRepositoryTest {

    @Test
    fun `upsertFromContact with same phone returns same id`() = runTest {
        val repo = PersonRepository(TestDatabase.create())
        val a = repo.upsertFromContact("Karim", "+8801712345678")
        val b = repo.upsertFromContact("Karim Hossain", "+8801712345678")
        assertEquals(a, b)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `upsertFromContact with null phone always inserts`() = runTest {
        val repo = PersonRepository(TestDatabase.create())
        val a = repo.upsertFromContact("Karim", null)
        val b = repo.upsertFromContact("Karim", null)
        assertTrue(a != b)
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `addManual never dedups`() = runTest {
        val repo = PersonRepository(TestDatabase.create())
        val a = repo.addManual("Karim")
        val b = repo.addManual("Karim")
        assertTrue(a != b)
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `observeAll returns persons with zero balance when no lend_borrow`() = runTest {
        val repo = PersonRepository(TestDatabase.create())
        repo.addManual("Karim")
        val all = repo.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(0.0, all[0].balance)
    }

    @Test
    fun `observeById returns null for missing person`() = runTest {
        val repo = PersonRepository(TestDatabase.create())
        assertEquals(null, repo.observeById("nonexistent").first())
    }

    @Test
    fun `observeById returns the person`() = runTest {
        val repo = PersonRepository(TestDatabase.create())
        val id = repo.addManual("Karim")
        val pwb = repo.observeById(id).first()
        assertNotNull(pwb)
        assertEquals("Karim", pwb.person.name)
    }
}
