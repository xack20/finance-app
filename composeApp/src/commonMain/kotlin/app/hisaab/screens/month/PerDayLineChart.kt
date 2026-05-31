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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.DayBucket
import app.hisaab.util.toTaka

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
    val dim = palette.accent.copy(alpha = 0.28f)

    Column {
        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val slot = size.width / (dayRange + 1)
                val barWidth = (slot * 0.6f).coerceIn(3f, 14f)
                buckets.forEach { bucket ->
                    val xFrac = (bucket.epochDay - minDay).toFloat() / dayRange.toFloat()
                    val x = xFrac * (size.width - barWidth)
                    val heightFrac = (bucket.total / maxAmount).toFloat()
                    val barHeight = (heightFrac * size.height).coerceAtLeast(6f)
                    drawRoundRect(
                        color = if (bucket.total >= maxAmount) accent else dim,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("৳0", color = palette.faint, fontSize = 11.sp)
            Text("${maxAmount.toTaka()} peak", color = palette.faint, fontSize = 11.sp)
        }
    }
}
