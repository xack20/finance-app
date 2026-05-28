package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.BudgetRow
import app.hisaab.domain.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

class BudgetRepository(private val db: HisaabDatabase) {

    fun observeActive(): Flow<List<BudgetRow>> =
        db.budgetQueriesQueries.observeActiveBudgets().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomainResolvingCategoryName() } }

    suspend fun set(categoryId: String, monthlyCapAmount: Double, startsMonth: YearMonth): String {
        val id = randomId()
        db.budgetQueriesQueries.insertBudget(
            id = id,
            category_id = categoryId,
            monthly_cap_amount = monthlyCapAmount,
            currency = "BDT",
            starts_month = startsMonth.value,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
        return id
    }

    suspend fun archive(id: String) {
        db.budgetQueriesQueries.archiveBudget(Clock.System.now().toEpochMilliseconds(), id)
    }

    private fun migrations.Budget.toDomainResolvingCategoryName(): BudgetRow {
        val categoryName = db.insightQueriesQueries.observeAllCategories()
            .executeAsList()
            .firstOrNull { it.id == category_id }?.name ?: ""
        return BudgetRow(
            id = id,
            categoryId = category_id,
            categoryName = categoryName,
            monthlyCapAmount = monthly_cap_amount,
            currency = currency,
            startsMonth = YearMonth(starts_month),
            archivedAt = archived_at,
            createdAt = created_at,
        )
    }

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
