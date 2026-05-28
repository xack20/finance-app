package app.hisaab.screens.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.YearMonth
import kotlin.math.abs

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
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        // Month switcher header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.previousMonth() }) {
                Text("‹", fontSize = 32.sp, color = palette.accent)
            }
            Text(
                formatYearMonth(ym),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.nextMonth() }) {
                Text("›", fontSize = 32.sp, color = palette.accent)
            }
        }
        Spacer(Modifier.height(20.dp))

        // Totals row
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            NetCell("In", totals.income, palette.positive)
            NetCell("Out", totals.expense, palette.negative)
            NetCell("Net", totals.net, palette.onBackground)
        }
        Spacer(Modifier.height(8.dp))
        val deltaNet = totals.net - totals.previousMonthNet
        val deltaArrow = if (deltaNet >= 0) "↑" else "↓"
        val deltaColor = if (deltaNet >= 0) palette.positive else palette.negative
        Text(
            "$deltaArrow ৳${abs(deltaNet).toInt()} vs last month",
            color = deltaColor,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(32.dp))
        SectionLabel("Categories", palette)
        CategoryBarChart(slices = categories.take(5), palette = palette)

        Spacer(Modifier.height(32.dp))
        SectionLabel("Per-day spending", palette)
        PerDayLineChart(buckets = perDay, palette = palette)

        Spacer(Modifier.height(32.dp))
        SectionLabel("Budgets", palette)
        BudgetProgressList(budgets = budgets, palette = palette)

        Spacer(Modifier.height(32.dp))
        SectionLabel("Recurring", palette)
        RecurringList(items = recurring, palette = palette)

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun NetCell(label: String, amount: Double, color: Color) {
    val palette = LocalHisaabPalette.current
    Column {
        Text(label.uppercase(), color = palette.muted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "৳${amount.toInt()}",
            color = color,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionLabel(text: String, palette: HisaabColors.Palette) {
    Text(
        text.uppercase(),
        color = palette.accent,
        letterSpacing = 2.sp,
        fontSize = 11.sp,
    )
    Spacer(Modifier.height(10.dp))
}

private fun formatYearMonth(ym: YearMonth): String {
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    return "${months[ym.month - 1]} ${ym.year}"
}
