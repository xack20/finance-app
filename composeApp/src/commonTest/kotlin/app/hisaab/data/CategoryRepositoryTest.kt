package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryRepositoryTest {

    @Test
    fun `ensureDefaults seeds 12 categories`() = runTest {
        val repo = CategoryRepository(TestDatabase.create())
        repo.ensureDefaults()
        val all = repo.observeAll().first()
        assertEquals(12, all.size)
        assertTrue(all.all { it.isDefault })
    }

    @Test
    fun `ensureDefaults is idempotent`() = runTest {
        val repo = CategoryRepository(TestDatabase.create())
        repo.ensureDefaults()
        repo.ensureDefaults()
        assertEquals(12, repo.observeAll().first().size)
    }

    @Test
    fun `add creates a non-default category`() = runTest {
        val repo = CategoryRepository(TestDatabase.create())
        val id = repo.add(name = "Pets", color = "#ad6b2a", icon = "🐱", parentId = null)
        val cats = repo.observeAll().first()
        assertEquals(1, cats.size)
        val pets = cats.first()
        assertEquals(id, pets.id)
        assertEquals("Pets", pets.name)
        assertTrue(!pets.isDefault)
    }

    @Test
    fun `default categories include food and transfer`() = runTest {
        val repo = CategoryRepository(TestDatabase.create())
        repo.ensureDefaults()
        val all = repo.observeAll().first()
        assertTrue(all.any { it.id == "food" && it.name == "Food & dining" })
        assertTrue(all.any { it.id == "transfer" && it.name == "Transfer" })
    }
}
