package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.LocalHisaabPalette

private const val EMPTY_SLOT = ' '
private const val BACKSPACE = '\b'

/**
 * The Midnight 3×4 on-screen numeric keypad (neo-onboarding.jsx:63-65 / neo.jsx:266). Rows are
 * 1-2-3 / 4-5-6 / 7-8-9 / [extraKey|blank]-0-backspace. Keys are surface tiles with a hairline
 * border, the digit in the display face; backspace renders the '⌫' (U+232B) glyph as text styled
 * exactly like the digit keys (neo-onboarding.jsx:64). Used by the OTP screen so it doesn't rely on
 * the OS keyboard.
 */
@Composable
fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    extraKey: Char? = null,
    onExtraKey: () -> Unit = {},
    keyHeight: Dp = 50.dp,
    gap: Dp = 8.dp,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(extraKey ?: EMPTY_SLOT, '0', BACKSPACE),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { key ->
                    when (key) {
                        EMPTY_SLOT -> Box(Modifier.weight(1f))
                        BACKSPACE -> KeypadTile(Modifier.weight(1f), keyHeight, onClick = onBackspace) {
                            Text(
                                "⌫",
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                                color = LocalHisaabPalette.current.onBackground,
                            )
                        }
                        else -> KeypadTile(
                            Modifier.weight(1f),
                            keyHeight,
                            onClick = { if (extraKey != null && key == extraKey) onExtraKey() else onDigit(key) },
                        ) {
                            Text(
                                key.toString(),
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                                color = LocalHisaabPalette.current.onBackground,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadTile(modifier: Modifier, height: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalHisaabPalette.current
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(p.surface)
            .border(1.dp, p.hair, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
