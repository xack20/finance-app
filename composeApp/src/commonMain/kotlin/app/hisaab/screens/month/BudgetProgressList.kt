package app.hisaab.screens.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.BudgetProgress

@Composable
fun BudgetProgressList(budgets: List<BudgetProgress>, palette: HisaabColors.Palette) {
    if (budgets.isEmpty()) {
        Text("Set a budget in Settings → Budgets.", color = palette.muted, fontSize = 13.sp)
        return
    }
    Column {
        budgets.forEach { progress ->
            BudgetProgressRow(progress = progress, palette = palette)
            HorizontalDivider(color = palette.rule)
        }
    }
}

@Composable
private fun BudgetProgressRow(progress: BudgetProgress, palette: HisaabColors.Palette) {
    val barColor = when {
        progress.percent >= 100.0 -> palette.negative
        progress.percent >= 80.0 -> palette.gold
        else -> palette.accent
    }
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(progress.budget.categoryName, color = palette.onBackground, modifier = Modifier.weight(1f))
            Text(
                "৳${progress.spent.toInt()} / ৳${progress.budget.monthlyCapAmount.toInt()}",
                color = palette.onBackground,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${progress.percent.toInt()}%",
                color = palette.muted,
                fontSize = 11.sp,
                modifier = Modifier.width(40.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).background(palette.rule),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.percent.toFloat().coerceAtMost(100f) / 100f)
                    .height(6.dp)
                    .background(barColor),
            )
        }
    }
}
