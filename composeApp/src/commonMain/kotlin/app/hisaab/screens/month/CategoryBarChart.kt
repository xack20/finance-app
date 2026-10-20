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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes
import app.hisaab.domain.CategorySlice
import app.hisaab.util.toTaka

/** Vivid Midnight category hues assigned by rank (design ignores the data color), per neo.jsx:327. */
private val VIVID_HUES = listOf("rose", "violet", "blue", "teal", "amber", "pink", "lime", "slate")

@Composable
fun CategoryBarChart(slices: List<CategorySlice>, palette: HisaabColors.Palette) {
    if (slices.isEmpty()) {
        Text("No expenses this month", color = palette.muted, fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slices.forEachIndexed { index, slice ->
            CategoryBar(slice = slice, hueIndex = index, palette = palette)
        }
    }
}

@Composable
private fun CategoryBar(slice: CategorySlice, hueIndex: Int, palette: HisaabColors.Palette) {
    val barColor = HisaabColors.categoryHues.getValue(VIVID_HUES[hueIndex % VIVID_HUES.size])
    val pill = HisaabShapes.pill
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                slice.categoryName,
                color = palette.onBackground,
                modifier = Modifier.weight(1f),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                slice.total.toTaka(),
                color = palette.onBackground,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${slice.percent.toInt()}%",
                color = palette.faint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
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
