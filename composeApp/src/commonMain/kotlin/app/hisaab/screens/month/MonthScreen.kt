package app.hisaab.screens.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.MoneyTone
import app.hisaab.design.components.SurfaceCard
import app.hisaab.domain.YearMonth

@Composable
fun MonthScreen() {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember {
        MonthViewModel(
            insightRepo = container.insightRepository,
            budgetRepo = container.budgetRepository,
        )
    }
    val ym by viewModel.yearMonth.collectAsState()
    val totals by viewModel.totals.collectAsState()
    val categories by viewModel.categoryBreakdown.collectAsState()
    val perDay by viewModel.perDaySpend.collectAsState()
    val recurring by viewModel.recurring.collectAsState()
    val budgets by viewModel.budgetProgress.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Month switcher
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = { viewModel.previousMonth() }) {
                Text("‹", fontSize = 28.sp, color = palette.muted)
            }
            Text(
                formatYearMonth(ym),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.nextMonth() }) {
                Text("›", fontSize = 28.sp, color = palette.muted)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Net card
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Eyebrow("Net this month")
            Spacer(Modifier.height(8.dp))
            MoneyText(
                amount = totals.net,
                signed = true,
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(12.dp))
            // Delta pill vs last month
            val delta = totals.net - totals.previousMonthNet
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(palette.accentSoft, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                MoneyText(
                    amount = delta,
                    signed = true,
                    tone = MoneyTone.Plain,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    " vs last month",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.muted,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Spending by category
        Eyebrow("Spending by category")
        Spacer(Modifier.height(10.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            CategoryBarChart(slices = categories.take(5), palette = palette)
        }

        Spacer(Modifier.height(24.dp))

        // Per-day spending
        Eyebrow("Per-day spending")
        Spacer(Modifier.height(10.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            PerDayLineChart(buckets = perDay, palette = palette)
        }

        Spacer(Modifier.height(24.dp))

        // Budgets
        Eyebrow("Budgets")
        Spacer(Modifier.height(10.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            BudgetProgressList(budgets = budgets, palette = palette)
        }

        Spacer(Modifier.height(24.dp))

        // Recurring
        Eyebrow("Recurring")
        Spacer(Modifier.height(10.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            RecurringList(items = recurring, palette = palette)
        }

        Spacer(Modifier.height(40.dp))
    }
}

private fun formatYearMonth(ym: YearMonth): String {
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    return "${months[ym.month - 1]} ${ym.year}"
}
