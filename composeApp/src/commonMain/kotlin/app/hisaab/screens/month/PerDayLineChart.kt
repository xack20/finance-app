package app.hisaab.screens.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes
import app.hisaab.domain.DayBucket
import app.hisaab.util.toTaka

private val CHART_HEIGHT = 90.dp
private val BAR_GAP = 7.dp
private val BAR_MIN = 6.dp

/** Full-width contiguous bar chart: one flex:1 bar per day (neo.jsx:318-322). */
@Composable
fun PerDayBarChart(buckets: List<DayBucket>, palette: HisaabColors.Palette) {
    if (buckets.isEmpty()) {
        Text("No spending this month", color = palette.muted, fontSize = 13.sp)
        return
    }
    val max = buckets.maxOf { it.total }.coerceAtLeast(1.0)
    val lime = palette.accent
    val limeDim = palette.accent.copy(alpha = 0.28f)
    val barShape = HisaabShapes.bar

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val frac = (bucket.total / max).toFloat()
                val barHeight = (CHART_HEIGHT * frac).coerceAtLeast(BAR_MIN)
                val isPeak = bucket.total >= max
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .clip(barShape)
                        .background(if (isPeak) lime else limeDim),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "৳0",
                color = palette.faint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
            )
            Text(
                "${max.toTaka()} peak",
                color = palette.faint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
            )
        }
    }
}
