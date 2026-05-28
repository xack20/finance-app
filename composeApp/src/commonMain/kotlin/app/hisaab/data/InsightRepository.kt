package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.BudgetProgress
import app.hisaab.domain.CategorySlice
import app.hisaab.domain.DayBucket
import app.hisaab.domain.MonthlyTotals
import app.hisaab.domain.RecurringHit
import app.hisaab.domain.YearMonth
import app.hisaab.util.monthRangeMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class InsightRepository(private val db: HisaabDatabase) {

    fun computeMonthlyTotals(yearMonth: YearMonth): Flow<MonthlyTotals> {
        val (start, end) = monthRangeMs(yearMonth)
        val (prevStart, prevEnd) = monthRangeMs(yearMonth.previous())
        return combine(
            db.insightQueriesQueries.monthlySumByKind(start, end).asFlow().mapToList(Dispatchers.Default),
            db.insightQueriesQueries.monthlySumByKind(prevStart, prevEnd).asFlow().mapToList(Dispatchers.Default),
        ) { current, previous ->
            val income = current.firstOrNull { it.kind == "INCOME" }?.total ?: 0.0
            val expense = current.firstOrNull { it.kind == "EXPENSE" }?.total ?: 0.0
            val prevIncome = previous.firstOrNull { it.kind == "INCOME" }?.total ?: 0.0
            val prevExpense = previous.firstOrNull { it.kind == "EXPENSE" }?.total ?: 0.0
            MonthlyTotals(
                yearMonth = yearMonth,
                income = income,
                expense = expense,
                net = income - expense,
                previousMonthNet = prevIncome - prevExpense,
            )
        }
    }

    fun computeCategoryBreakdown(yearMonth: YearMonth): Flow<List<CategorySlice>> {
        val (start, end) = monthRangeMs(yearMonth)
        return db.insightQueriesQueries.categoryBreakdownForRange(start, end).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                val total = rows.sumOf { it.total ?: 0.0 }
                rows.map { r ->
                    val amount = r.total ?: 0.0
                    CategorySlice(
                        categoryId = r.category_id,
                        categoryName = r.category_name,
                        categoryColor = r.category_color,
                        total = amount,
                        percent = if (total > 0) amount / total * 100.0 else 0.0,
                    )
                }
            }
    }

    fun computePerDaySpend(yearMonth: YearMonth): Flow<List<DayBucket>> {
        val (start, end) = monthRangeMs(yearMonth)
        return db.insightQueriesQueries.perDaySpendForRange(start, end).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { DayBucket(it.epoch_day, it.total ?: 0.0) } }
    }

    fun detectRecurring(): Flow<List<RecurringHit>> {
        val ninetyDaysAgo = Clock.System.now().toEpochMilliseconds() - (90L * 86_400_000L)
        return db.insightQueriesQueries.recurringCandidates(ninetyDaysAgo).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map {
                    RecurringHit(
                        merchantId = it.merchant_id,
                        merchantName = it.merchant_name,
                        occurrenceCount = it.occurrence_count,
                        avgAmount = it.avg_amount ?: 0.0,
                        lastSeenTs = it.last_seen_ts ?: 0L,
                    )
                }
            }
    }

    fun computeBudgetProgress(
        yearMonth: YearMonth,
        budgetRepo: BudgetRepository,
    ): Flow<List<BudgetProgress>> {
        val (start, end) = monthRangeMs(yearMonth)
        return budgetRepo.observeActive().map { budgets ->
            budgets.map { b ->
                val spent = db.insightQueriesQueries
                    .budgetSpendForCategory(b.categoryId, start, end)
                    .executeAsOneOrNull()?.total ?: 0.0
                BudgetProgress(
                    budget = b,
                    spent = spent,
                    percent = if (b.monthlyCapAmount > 0) spent / b.monthlyCapAmount * 100.0 else 0.0,
                )
            }
        }
    }
}
