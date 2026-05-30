package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.Account
import app.hisaab.domain.AccountKind
import app.hisaab.domain.CardSummaryCalculator
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val coroutineScope = rememberCoroutineScope()
    val accounts by container.accountRepository.observeActive().collectAsState(initial = emptyList())
    var showAddSheet by remember { mutableStateOf(false) }
    var renamingAccount by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts", color = palette.onBackground) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = palette.muted) } },
                actions = { TextButton(onClick = { showAddSheet = true }) { Text("+ Add", color = palette.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            items(accounts, key = { it.id }) { acc ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { renamingAccount = acc }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(acc.name, color = palette.onBackground)
                            Spacer(Modifier.height(2.dp))
                            Text("${acc.kind} · ${acc.currency}", color = palette.muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = {
                            coroutineScope.launch { container.accountRepository.archive(acc.id) }
                        }) { Text("Archive", color = palette.negative, fontSize = 12.sp) }
                    }

                    if (acc.kind == AccountKind.CARD) {
                        CardSummaryRow(acc = acc)
                    }

                    HorizontalDivider(color = palette.rule)
                }
            }
        }
    }

    if (showAddSheet) {
        AddAccountSheet(
            onAdd = { name, kind, creditLimit, statementDay, dueDay ->
                coroutineScope.launch {
                    container.accountRepository.add(
                        name = name,
                        kind = kind,
                        institution = null,
                        creditLimit = if (kind == AccountKind.CARD) creditLimit else null,
                        statementDay = if (kind == AccountKind.CARD) statementDay else null,
                        dueDay = if (kind == AccountKind.CARD) dueDay else null,
                    )
                }
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }

    renamingAccount?.let { acc ->
        RenameAccountDialog(
            current = acc.name,
            onSave = { newName ->
                coroutineScope.launch { container.accountRepository.rename(acc.id, newName) }
                renamingAccount = null
            },
            onDismiss = { renamingAccount = null },
        )
    }
}

@Composable
private fun CardSummaryRow(acc: Account) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current

    val cl = acc.creditLimit
    val sd = acc.statementDay
    val dd = acc.dueDay

    val outstanding by produceState(initialValue = null as Double?, acc.id) {
        value = container.accountRepository.cardOutstanding(acc.id)
    }

    val os = outstanding
    if (os != null) {
        val available = if (cl != null) cl - os else null
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val nextDue = if (sd != null && dd != null) {
            CardSummaryCalculator.nextDueDate(today, sd, dd)
        } else null

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CardSummaryChip(label = "Outstanding", value = "৳${"%.2f".format(os)}", palette.negative)
                if (available != null) {
                    CardSummaryChip(label = "Available", value = "৳${"%.2f".format(available)}", palette.positive)
                }
                if (nextDue != null) {
                    CardSummaryChip(label = "Due", value = nextDue.toString(), palette.muted)
                }
            }
        }
    }
}

@Composable
private fun CardSummaryChip(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    val palette = LocalHisaabPalette.current
    Column {
        Text(label, color = palette.muted, fontSize = 9.sp)
        Text(value, color = valueColor, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(
    onAdd: (name: String, kind: AccountKind, creditLimit: Double?, statementDay: Int?, dueDay: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AccountKind.CASH) }

    // Card-specific fields
    var creditLimitText by remember { mutableStateOf("") }
    var statementDayText by remember { mutableStateOf("") }
    var dueDayText by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.background) {
        Column(modifier = Modifier.padding(22.dp).fillMaxWidth()) {
            Text("New account", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Text("Kind", color = palette.muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccountKind.entries.forEach { k ->
                    Button(
                        onClick = { kind = k },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (kind == k) palette.accent else palette.surface,
                            contentColor = if (kind == k) palette.background else palette.onBackground,
                        ),
                    ) { Text(k.name, fontSize = 11.sp) }
                }
            }

            if (kind == AccountKind.CARD) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = creditLimitText,
                    onValueChange = { creditLimitText = it },
                    label = { Text("Credit limit (optional)", color = palette.muted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = statementDayText,
                        onValueChange = { v ->
                            statementDayText = v.filter { it.isDigit() }.take(2)
                        },
                        label = { Text("Statement day (1–28)", color = palette.muted) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = dueDayText,
                        onValueChange = { v ->
                            dueDayText = v.filter { it.isDigit() }.take(2)
                        },
                        label = { Text("Due day (1–28)", color = palette.muted) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val creditLimit = creditLimitText.toDoubleOrNull()
                        val statementDay = if (kind == AccountKind.CARD) {
                            clampDay(statementDayText.toIntOrNull())
                        } else null
                        val dueDay = if (kind == AccountKind.CARD) {
                            clampDay(dueDayText.toIntOrNull())
                        } else null
                        onAdd(name.trim(), kind, creditLimit, statementDay, dueDay)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) { Text("Add", color = palette.background) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Clamps a day value to 1..28. Returns null if the input is null. */
private fun clampDay(day: Int?): Int? = day?.coerceIn(1, 28)

@Composable
private fun RenameAccountDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalHisaabPalette.current
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename account", color = palette.onBackground) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && name != current) onSave(name.trim()) }) {
                Text("Save", color = palette.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = palette.muted) } },
    )
}
