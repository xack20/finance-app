package app.hisaab.screens.month

import app.hisaab.data.BudgetRepository
import app.hisaab.data.InsightRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() { Dispatchers.setMain(dispatcher) }

    @AfterTest
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `previousMonth decrements the year-month`() = runTest {
        val db = TestDatabase.create()
        val vm = MonthViewModel(
            insightRepo = InsightRepository(db),
            budgetRepo = BudgetRepository(db),
            scope = CoroutineScope(dispatcher + SupervisorJob()),
        )
        vm.selectMonth(YearMonth.of(2026, 5))
        vm.previousMonth()
        assertEquals(YearMonth.of(2026, 4), vm.yearMonth.value)
    }

    @Test
    fun `previousMonth across year boundary`() = runTest {
        val db = TestDatabase.create()
        val vm = MonthViewModel(
            insightRepo = InsightRepository(db),
            budgetRepo = BudgetRepository(db),
            scope = CoroutineScope(dispatcher + SupervisorJob()),
        )
        vm.selectMonth(YearMonth.of(2026, 1))
        vm.previousMonth()
        assertEquals(YearMonth.of(2025, 12), vm.yearMonth.value)
    }

    @Test
    fun `nextMonth increments and wraps year`() = runTest {
        val db = TestDatabase.create()
        val vm = MonthViewModel(
            insightRepo = InsightRepository(db),
            budgetRepo = BudgetRepository(db),
            scope = CoroutineScope(dispatcher + SupervisorJob()),
        )
        vm.selectMonth(YearMonth.of(2026, 12))
        vm.nextMonth()
        assertEquals(YearMonth.of(2027, 1), vm.yearMonth.value)
    }
}
