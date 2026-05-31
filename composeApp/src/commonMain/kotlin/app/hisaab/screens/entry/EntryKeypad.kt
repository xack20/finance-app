package app.hisaab.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.TxnKind
import app.hisaab.util.toTaka

/** Big live amount, ৳ prefix, grouped, colored by [kind]. Empty/zero shows a faint "0". */
@Composable
fun BigAmount(amount: String, kind: TxnKind, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val isZero = amount.isEmpty() || (amount.toDoubleOrNull()?.let { it == 0.0 } ?: false)
    val color = when {
        isZero -> p.faint
        kind == TxnKind.INCOME -> p.positive
        kind == TxnKind.EXPENSE -> p.onBackground
        else -> p.accent
    }
    val display = run {
        val n = amount.toDoubleOrNull()
        when {
            amount.isEmpty() -> "৳0"
            n == null -> "৳$amount"
            else -> {
                val dec = amount.substringAfter('.', "").length.coerceAtMost(2)
                val grouped = n.toTaka(decimals = dec)
                if (amount.endsWith(".")) "$grouped." else grouped
            }
        }
    }
    Text(
        display,
        modifier = modifier.fillMaxWidth(),
        color = color,
        fontSize = 56.sp,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.displayLarge,
    )
}

/** Midnight kind chips: selected = onBackground fill + background label; else transparent + hair. */
@Composable
fun KindChipRow(kind: TxnKind, onSelect: (TxnKind) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val kinds = listOf(
        TxnKind.EXPENSE to "Expense", TxnKind.INCOME to "Income",
        TxnKind.TRANSFER to "Transfer", TxnKind.LEND to "Lend", TxnKind.BORROW to "Borrow",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.forEach { (k, label) ->
            val on = k == kind
            Text(
                label,
                color = if (on) p.background else p.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(HisaabShapes.pill)
                    .background(if (on) p.onBackground else Color.Transparent)
                    .border(1.dp, if (on) p.onBackground else p.hair, HisaabShapes.pill)
                    .clickable { onSelect(k) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** 3x4 numeric keypad: 1-9, '.', 0, ⌫. Emits each key char (backspace = [BACKSPACE]). */
@Composable
fun NumericKeypad(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('.', '0', BACKSPACE),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key -> KeypadKey(key, onKey, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: Char, onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Text(
        if (key == BACKSPACE) "⌫" else key.toString(),
        modifier = modifier
            .height(56.dp)
            .clip(HisaabShapes.field)
            .background(p.surface)
            .clickable { onKey(key) }
            .padding(vertical = 14.dp),
        color = if (key == BACKSPACE) p.muted else p.onBackground,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}
