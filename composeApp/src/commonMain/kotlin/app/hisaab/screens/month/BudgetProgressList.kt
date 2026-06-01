package app.hisaab.screens.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.BudgetProgress
import app.hisaab.util.toTaka

@Composable
fun BudgetProgressList(budgets: List<BudgetProgress>, palette: HisaabColors.Palette) {
    if (budgets.isEmpty()) {
        Text("Set a budget in Settings → Budgets.", color = palette.muted, fontSize = 13.sp)
        return
    }
    // Single evenly-distributed row (CSS justify-content: space-around) — neo.jsx:335.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Top,
    ) {
        budgets.forEach { BudgetRing(it, palette) }
    }
}

@Composable
private fun BudgetRing(progress: BudgetProgress, palette: HisaabColors.Palette) {
    val pct = progress.percent.toFloat()
    val ringColor = when {
        pct >= 100f -> palette.negative
        pct >= 80f -> HisaabColors.categoryHues.getValue("amber")
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
                drawArc(
                    color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = stroke,
                )
                drawArc(
                    color = ringColor, startAngle = -90f,
                    sweepAngle = 360f * (pct.coerceAtMost(100f) / 100f), useCenter = false,
                    topLeft = topLeft, size = arcSize, style = stroke,
                )
            }
            // center %: mono, 13, weight 700 (neo.jsx:300)
            Text(
                "${progress.percent.toInt()}%",
                color = if (pct >= 100f) palette.negative else palette.onBackground,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.height(8.dp))
        // label: sans, 12.5, weight 600 (neo.jsx:302)
        Text(
            progress.budget.categoryName,
            color = palette.onBackground,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        // value: mono, 11, muted (neo.jsx:302)
        Text(
            "${progress.spent.toTaka()}/${progress.budget.monthlyCapAmount.toTaka()}",
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
        )
    }
}
