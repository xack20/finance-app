package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.*
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

    val balances by produceState(initialValue = emptyMap<String, Double>(), accounts) {
        value = container.accountRepository.accountBalances()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = palette.muted) }
                },
                actions = {
                    TextButton(onClick = { showAddSheet = true }) {
                        Text("+ Add", color = palette.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HisaabSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
            contentPadding = PaddingValues(bottom = HisaabSpacing.xl),
        ) {
            item {
                SectionHeader("Accounts", modifier = Modifier.padding(vertical = HisaabSpacing.lg))
            }
            items(accounts, key = { it.id }) { acc ->
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    // Name + Eyebrow row with trailing balance
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { renamingAccount = acc },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                acc.name,
                                color = palette.onBackground,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(HisaabSpacing.xs))
                            Eyebrow("${acc.kind} · ${acc.currency}")
                        }
                        MoneyText(
                            amount = balances[acc.id] ?: 0.0,
                            signed = true,
                            tone = MoneyTone.Auto,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // CARD extras
                    if (acc.kind == AccountKind.CARD) {
                        Spacer(Modifier.height(HisaabSpacing.md))
                        HorizontalDivider(color = palette.hair, thickness = 0.5.dp)
                        Spacer(Modifier.height(HisaabSpacing.md))
                        CardSummaryRow(acc = acc)
                    }

                    // Archive affordance
                    Spacer(Modifier.height(HisaabSpacing.sm))
                    HorizontalDivider(color = palette.hair, thickness = 0.5.dp)
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = {
                            coroutineScope.launch { container.accountRepository.archive(acc.id) }
                        }) { Text("Archive", color = palette.negative, fontSize = 12.sp) }
                    }
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
    val container = LocalAppContainer.current
    val palette = LocalHisaabPalette.current

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.lg),
        ) {
            // Outstanding — negative tone (os is a positive debt amount; negate to render red)
            Column {
                Eyebrow("Outstanding")
                Spacer(Modifier.height(HisaabSpacing.xs))
                MoneyText(
                    amount = -os,
                    signed = false,
                    tone = MoneyTone.Auto,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (available != null) {
                Column {
                    Eyebrow("Available")
                    Spacer(Modifier.height(HisaabSpacing.xs))
                    MoneyText(
                        amount = available,
                        signed = false,
                        tone = MoneyTone.Auto,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // Due date is a LocalDate, not a money amount — render as plain labeled text
            if (nextDue != null) {
                Column {
                    Eyebrow("Due")
                    Spacer(Modifier.height(HisaabSpacing.xs))
                    Text(
                        nextDue.toString(),
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
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

    MidnightSheet(onDismiss = onDismiss, sheetState = sheetState, title = "New account") {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name", color = palette.muted) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(HisaabSpacing.md))
        Text("Kind", color = palette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(HisaabSpacing.xs))
        // Kind selector: pill chips (selected = accent fill, unselected = hair border + muted text)
        Row(
            horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AccountKind.entries.forEach { k ->
                val selected = kind == k
                Box(
                    modifier = Modifier
                        .clickable { kind = k }
                        .background(
                            color = if (selected) palette.accent else palette.surface,
                            shape = HisaabShapes.pill,
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) palette.accent else palette.hair,
                            shape = HisaabShapes.pill,
                        )
                        .padding(horizontal = HisaabSpacing.md, vertical = HisaabSpacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = k.name,
                        color = if (selected) palette.onAccent else palette.muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        if (kind == AccountKind.CARD) {
            Spacer(Modifier.height(HisaabSpacing.md))
            OutlinedTextField(
                value = creditLimitText,
                onValueChange = { creditLimitText = it },
                label = { Text("৳ Credit limit (optional)", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(HisaabSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm)) {
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

        Spacer(Modifier.height(HisaabSpacing.lg))
        PrimaryButton(
            text = "Add account",
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
            enabled = name.isNotBlank(),
        )
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
        containerColor = palette.surface,
        shape = HisaabShapes.card,
    )
}
