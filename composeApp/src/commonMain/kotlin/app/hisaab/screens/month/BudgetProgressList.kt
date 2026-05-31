package app.hisaab.screens.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.BudgetProgress

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetProgressList(budgets: List<BudgetProgress>, palette: HisaabColors.Palette) {
    if (budgets.isEmpty()) {
        Text("Set a budget in Settings → Budgets.", color = palette.muted, fontSize = 13.sp)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        budgets.forEach { BudgetRing(it, palette) }
    }
}

@Composable
private fun BudgetRing(progress: BudgetProgress, palette: HisaabColors.Palette) {
    val pct = progress.percent.toFloat()
    val ringColor = when {
        pct >= 100f -> palette.negative
        pct >= 80f -> app.hisaab.design.HisaabColors.categoryHues.getValue("amber")
        else -> palette.accent
    }
    val track = palette.backgroundInset
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(64.dp)) {
                val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                val inset = 6.dp.toPx() / 2f
                val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                val topLeft = Offset(inset, inset)
                drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = stroke)
                drawArc(color = ringColor, startAngle = -90f,
                    sweepAngle = 360f * (pct.coerceAtMost(100f) / 100f), useCenter = false,
                    topLeft = topLeft, size = arcSize, style = stroke)
            }
            Text(
                "${progress.percent.toInt()}%",
                color = if (pct >= 100f) palette.negative else palette.onBackground,
                fontSize = 13.sp,
            )
        }
        Text(progress.budget.categoryName, color = palette.muted, fontSize = 12.sp)
        Text(
            "৳${progress.spent.toInt()}/৳${progress.budget.monthlyCapAmount.toInt()}",
            color = palette.muted, fontSize = 11.sp,
        )
    }
}
