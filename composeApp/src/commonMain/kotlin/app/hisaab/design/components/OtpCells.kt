package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
            // Design (neo-onboarding.jsx:60): the active caret cell wins — lime border + 4px lime-soft
            // glow ring — even in the error state; only the *other* cells turn red. Background stays surface.
            val borderColor = when {
                active -> p.accent
                isError -> p.negative
                else -> p.hair
            }
            Box(
                Modifier
                    .weight(1f)
                    // Uniform 4dp inset on every cell keeps widths aligned; the active cell paints a
                    // lime-soft ring in that margin (box-shadow 0 0 0 4px var(--lime-soft)).
                    .then(if (active) Modifier.background(p.accentSoft, RoundedCornerShape(18.dp)) else Modifier)
                    .padding(4.dp)
                    .height(58.dp)
                    .clip(HisaabShapes.field)
                    .background(p.surface, HisaabShapes.field)
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
