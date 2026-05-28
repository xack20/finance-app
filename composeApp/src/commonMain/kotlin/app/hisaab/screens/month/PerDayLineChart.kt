package app.hisaab.screens.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.DayBucket

@Composable
fun PerDayLineChart(buckets: List<DayBucket>, palette: HisaabColors.Palette) {
    if (buckets.isEmpty()) {
        Text("No spending this month", color = palette.muted, fontSize = 13.sp)
        return
    }
    val maxAmount = buckets.maxOf { it.total }
    val minDay = buckets.minOf { it.epochDay }
    val maxDay = buckets.maxOf { it.epochDay }
    val dayRange = (maxDay - minDay).coerceAtLeast(1L)
    val accent = palette.accent
    val rule = palette.rule

    Column {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barWidth = (size.width / (dayRange + 1)).coerceAtLeast(2f)
                buckets.forEach { bucket ->
                    val xFrac = (bucket.epochDay - minDay).toFloat() / dayRange.toFloat()
                    val x = xFrac * (size.width - barWidth)
                    val heightFrac = (bucket.total / maxAmount).toFloat()
                    val barHeight = heightFrac * size.height
                    drawRect(
                        color = accent,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth - 2f, barHeight),
                    )
                }
                // Baseline
                drawLine(
                    color = rule,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("৳0", color = palette.muted, fontSize = 10.sp)
            Text("৳${maxAmount.toInt()} max", color = palette.muted, fontSize = 10.sp)
        }
    }
}
