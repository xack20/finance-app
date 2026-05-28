package app.hisaab.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.LendBorrowRow
import app.hisaab.domain.LendBorrowStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(personId: String, onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember {
        PeopleViewModel(
            personRepo = container.personRepository,
            lendBorrowRepo = container.lendBorrowRepository,
            accountRepo = container.accountRepository,
        )
    }
    val detail by viewModel.personDetail(personId).collectAsState()
    val accounts by viewModel.activeAccounts.collectAsState()
    var settleRecord by remember { mutableStateOf<LendBorrowRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.person?.name ?: "Person", color = palette.onBackground) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = palette.muted) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        val d = detail
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(12.dp))
                val (label, color) = when {
                    d.balance > 0 -> "They owe you" to palette.positive
                    d.balance < 0 -> "You owe them" to palette.negative
                    else -> "Settled" to palette.muted
                }
                Text(label, color = palette.muted, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (d.balance == 0.0) "৳0"
                    else "৳${kotlin.math.abs(d.balance).toInt()}",
                    color = color,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                d.person.contactRef?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = palette.muted, fontSize = 13.sp)
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "History",
                    color = palette.accent,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (d.records.isEmpty()) {
                    Text("No records yet.", color = palette.muted, fontSize = 13.sp)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(d.records, key = { it.id }) { row ->
                            LendBorrowRowItem(
                                row = row,
                                palette = palette,
                                onSettle = { settleRecord = row },
                            )
                            HorizontalDivider(color = palette.rule)
                        }
                    }
                }
            }
        }
    }

    val record = settleRecord
    if (record != null) {
        SettleDialog(
            record = record,
            accounts = accounts,
            onConfirm = { amount, accountId ->
                viewModel.settle(record.id, amount, accountId)
                settleRecord = null
            },
            onDismiss = { settleRecord = null },
            palette = palette,
        )
    }
}

@Composable
private fun LendBorrowRowItem(
    row: LendBorrowRow,
    palette: HisaabColors.Palette,
    onSettle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val direction = if (row.direction == LendBorrowDirection.LENT) "Lent" else "Borrowed"
            Text("$direction ৳${row.amount.toInt()}", color = palette.onBackground)
            row.purpose?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = palette.muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            val statusColor = when (row.status) {
                LendBorrowStatus.SETTLED -> palette.positive
                LendBorrowStatus.PARTIAL -> palette.gold
                LendBorrowStatus.OPEN -> palette.muted
            }
            Text(row.status.name, color = statusColor, fontSize = 10.sp, letterSpacing = 1.sp)
        }
        if (row.status != LendBorrowStatus.SETTLED) {
            TextButton(onClick = onSettle) { Text("Settle", color = palette.accent) }
        }
    }
}

@Composable
private fun SettleDialog(
    record: LendBorrowRow,
    accounts: List<app.hisaab.domain.Account>,
    onConfirm: (Double, String) -> Unit,
    onDismiss: () -> Unit,
    palette: HisaabColors.Palette,
) {
    var amount by remember { mutableStateOf(record.amount.toInt().toString()) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settle ৳${record.amount.toInt()}", color = palette.onBackground) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount", color = palette.muted) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text("Account", color = palette.muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                accounts.forEach { acc ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = acc.id == accountId,
                            onClick = { accountId = acc.id },
                            colors = RadioButtonDefaults.colors(selectedColor = palette.accent),
                        )
                        Text(acc.name, color = palette.onBackground)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = amount.toDoubleOrNull()
                    if (a != null && a > 0 && accountId.isNotBlank()) {
                        onConfirm(a, accountId)
                    }
                },
            ) { Text("Settle", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.muted) }
        },
    )
}
