package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetRepositoryTest {

    @Test
    fun `set then observeActive returns the new budget`() = runTest {
        val db = TestDatabase.create()
        val catRepo = CategoryRepository(db)
        catRepo.ensureDefaults()
        val repo = BudgetRepository(db)
        val id = repo.set(categoryId = "food", monthlyCapAmount = 5000.0, startsMonth = YearMonth.of(2026, 5))
        val budgets = repo.observeActive().first()
        assertEquals(1, budgets.size)
        assertEquals(id, budgets[0].id)
        assertEquals("Food & dining", budgets[0].categoryName)
        assertEquals(5000.0, budgets[0].monthlyCapAmount)
    }

    @Test
    fun `archive removes from observeActive`() = runTest {
        val db = TestDatabase.create()
        val catRepo = CategoryRepository(db)
        catRepo.ensureDefaults()
        val repo = BudgetRepository(db)
        val id = repo.set("food", 5000.0, YearMonth.of(2026, 5))
        repo.archive(id)
        assertEquals(0, repo.observeActive().first().size)
    }

    @Test
    fun `multiple budgets for different categories coexist`() = runTest {
        val db = TestDatabase.create()
        val catRepo = CategoryRepository(db)
        catRepo.ensureDefaults()
        val repo = BudgetRepository(db)
        repo.set("food", 5000.0, YearMonth.of(2026, 5))
        repo.set("transport", 3000.0, YearMonth.of(2026, 5))
        val budgets = repo.observeActive().first()
        assertEquals(2, budgets.size)
        assertTrue(budgets.any { it.categoryName == "Food & dining" })
        assertTrue(budgets.any { it.categoryName == "Transport" })
    }
}
