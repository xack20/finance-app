package app.hisaab.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HisaabIcon
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
    // Number only — the ৳ mark is rendered as a separate faint span (neo.jsx:239-242).
    val display = run {
        val n = amount.toDoubleOrNull()
        when {
            amount.isEmpty() -> "0"
            n == null -> amount
            else -> {
                val dec = amount.substringAfter('.', "").length.coerceAtMost(2)
                val grouped = n.toTaka(decimals = dec).removePrefix("৳")
                if (amount.endsWith(".")) "$grouped." else grouped
            }
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "৳",
            color = p.faint,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            display,
            color = color,
            fontSize = 64.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.03).em,
            // Space Mono tabular (bodySmall family), matching the design's `disp mono` hero amount.
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Midnight kind chips: selected = onBackground fill + background label; else transparent + hair. */
@Composable
fun KindChipRow(kind: TxnKind, onSelect: (TxnKind) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val kinds = listOf(
        TxnKind.EXPENSE to "Expense", TxnKind.INCOME to "Income",
        TxnKind.TRANSFER to "Transfer", TxnKind.LEND to "Lend", TxnKind.BORROW to "Borrow",
    )
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
    Box(
        modifier
            .height(56.dp)
            .clip(HisaabShapes.field)
            .background(p.surface)
            .clickable { onKey(key) },
        contentAlignment = Alignment.Center,
    ) {
        if (key == BACKSPACE) {
            // 'del' renders as the chevron-left stroke icon, muted, 22px (neo.jsx:266).
            HisaabIcon("chevron-left", tint = p.muted, size = 22.dp)
        } else {
            Text(
                key.toString(),
                color = p.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                // Space Grotesk display face for keypad digits (neo.jsx:266).
                style = MaterialTheme.typography.displayLarge,
            )
        }
    }
}
