package app.hisaab.screens.month

import app.hisaab.data.BudgetRepository
import app.hisaab.data.InsightRepository
import app.hisaab.domain.BudgetProgress
import app.hisaab.domain.CategorySlice
import app.hisaab.domain.DayBucket
import app.hisaab.domain.MonthlyTotals
import app.hisaab.domain.RecurringHit
import app.hisaab.domain.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(
    private val insightRepo: InsightRepository,
    private val budgetRepo: BudgetRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _yearMonth = MutableStateFlow(currentYearMonth())
    val yearMonth: StateFlow<YearMonth> = _yearMonth

    val totals: StateFlow<MonthlyTotals> = _yearMonth
        .flatMapLatest { ym -> insightRepo.computeMonthlyTotals(ym) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000),
            MonthlyTotals(currentYearMonth(), 0.0, 0.0, 0.0, 0.0))

    val categoryBreakdown: StateFlow<List<CategorySlice>> = _yearMonth
        .flatMapLatest { ym -> insightRepo.computeCategoryBreakdown(ym) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val perDaySpend: StateFlow<List<DayBucket>> = _yearMonth
        .flatMapLatest { ym -> insightRepo.computePerDaySpend(ym) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recurring: StateFlow<List<RecurringHit>> = insightRepo.detectRecurring()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val budgetProgress: StateFlow<List<BudgetProgress>> = _yearMonth
        .flatMapLatest { ym -> insightRepo.computeBudgetProgress(ym, budgetRepo) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectMonth(ym: YearMonth) { _yearMonth.value = ym }

    fun previousMonth() { _yearMonth.value = _yearMonth.value.previous() }

    fun nextMonth() {
        val ym = _yearMonth.value
        val next = if (ym.month == 12) YearMonth.of(ym.year + 1, 1)
                   else YearMonth.of(ym.year, ym.month + 1)
        _yearMonth.value = next
    }

    private fun currentYearMonth(): YearMonth {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return YearMonth.of(now.year, now.monthNumber)
    }
}
