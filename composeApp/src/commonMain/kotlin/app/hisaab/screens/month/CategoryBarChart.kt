package app.hisaab.screens.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.CategorySlice
import app.hisaab.util.toTaka

@Composable
fun CategoryBarChart(slices: List<CategorySlice>, palette: HisaabColors.Palette) {
    if (slices.isEmpty()) {
        Text("No expenses this month", color = palette.muted, fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slices.forEach { slice ->
            CategoryBar(slice = slice, palette = palette)
        }
    }
}

@Composable
private fun CategoryBar(slice: CategorySlice, palette: HisaabColors.Palette) {
    val barColor = parseColorOrAccent(slice.categoryColor, palette.accent)
    val pill = RoundedCornerShape(999.dp)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                slice.categoryName,
                color = palette.onBackground,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
            )
            Text(
                slice.total.toTaka(),
                color = palette.onBackground,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${slice.percent.toInt()}%",
                color = palette.faint,
                fontSize = 11.sp,
                modifier = Modifier.width(36.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(pill)
                .background(palette.backgroundInset),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(slice.percent.toFloat() / 100f)
                    .height(7.dp)
                    .clip(pill)
                    .background(barColor),
            )
        }
    }
}

private fun parseColorOrAccent(hex: String?, fallback: Color): Color {
    if (hex == null) return fallback
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return fallback
    return try {
        val rgb = cleaned.toLong(16)
        Color(
            red = ((rgb shr 16) and 0xFF).toInt() / 255f,
            green = ((rgb shr 8) and 0xFF).toInt() / 255f,
            blue = (rgb and 0xFF).toInt() / 255f,
        )
    } catch (_: NumberFormatException) {
        fallback
    }
}
