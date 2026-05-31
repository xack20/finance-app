package app.hisaab.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.Category
import app.hisaab.domain.NewSplitTransaction
import app.hisaab.domain.TxnKind
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditorSheet(
    initialSplits: List<NewSplitTransaction>,
    parentAmount: Double,
    categories: List<Category>,
    onSave: (List<NewSplitTransaction>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    val sheetState = rememberModalBottomSheetState()

    val rows = remember {
        mutableStateListOf<NewSplitTransaction>().apply {
            if (initialSplits.isEmpty()) {
                add(NewSplitTransaction(0.0, null, null, TxnKind.EXPENSE))
                add(NewSplitTransaction(0.0, null, null, TxnKind.EXPENSE))
            } else {
                addAll(initialSplits)
            }
        }
    }

    val childSum = rows.sumOf { it.amount }
    val matches = abs(childSum - parentAmount) < 0.01

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
    ) {
        Column(
            modifier = Modifier
                .padding(HisaabSpacing.gutter)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Split into parts", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Parent: ৳${parentAmount.toInt()} — children: ৳${childSum.toInt()}",
                color = if (matches) palette.positive else palette.muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))

            rows.forEachIndexed { idx, row ->
                SplitRow(
                    index = idx,
                    row = row,
                    onChange = { updated -> rows[idx] = updated },
                    onRemove = { if (rows.size > 1) rows.removeAt(idx) },
                )
                Spacer(Modifier.height(8.dp))
            }

            TextButton(onClick = {
                rows.add(NewSplitTransaction(0.0, null, null, TxnKind.EXPENSE))
            }) { Text("+ Add part", color = palette.accent) }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = { onSave(emptyList()) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Remove splits", color = palette.muted)
                }
                Button(
                    onClick = { onSave(rows.toList()) },
                    modifier = Modifier.weight(1f),
                    enabled = matches && rows.all { it.amount > 0 },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                ) { Text("Save", color = palette.background) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SplitRow(
    index: Int,
    row: NewSplitTransaction,
    onChange: (NewSplitTransaction) -> Unit,
    onRemove: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${index + 1}.", color = palette.muted, modifier = Modifier.width(28.dp))
        OutlinedTextField(
            value = if (row.amount == 0.0) "" else row.amount.toString(),
            onValueChange = { input ->
                val parsed = input.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                onChange(row.copy(amount = parsed))
            },
            label = { Text("Amount", color = palette.muted, fontSize = 11.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRemove) { Text("×", color = palette.muted, fontSize = 18.sp) }
    }
}
