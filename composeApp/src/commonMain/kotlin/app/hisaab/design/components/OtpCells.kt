package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/**
 * Segmented OTP code display: [length] cells with mono digits. The active cell (index == value
 * length) gets a 1.5px lime border + lime-soft fill (the design's 4px lime-soft glow ring). Display
 * only — input is driven by the on-screen [NumericKeypad], so there is no hidden OS-keyboard field.
 */
@Composable
fun OtpCells(
    value: String,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
) {
    val p = LocalHisaabPalette.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(length) { i ->
            val filled = i < value.length
            val active = i == value.length
            val borderColor = when {
                isError -> p.negative
                active -> p.accent
                else -> p.hair
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(HisaabShapes.field)
                    .background(if (active) p.accentSoft else p.surface, HisaabShapes.field)
                    .border(1.5.dp, borderColor, HisaabShapes.field),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (filled) value[i].toString() else "",
                    color = p.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
