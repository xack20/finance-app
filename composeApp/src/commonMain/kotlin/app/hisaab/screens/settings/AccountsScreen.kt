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
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.datetime.LocalDate
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

    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        NeoTopBar(
            title = "Accounts",
            onBack = onBack,
            rightLabel = "+ Add",
            onRight = { showAddSheet = true },
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HisaabSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HisaabSpacing.md),
            contentPadding = PaddingValues(top = HisaabSpacing.sm, bottom = HisaabSpacing.xl),
        ) {
            items(accounts, key = { it.id }) { acc ->
                AccountCard(
                    acc = acc,
                    balance = balances[acc.id] ?: 0.0,
                    onRename = { renamingAccount = acc },
                )
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
private fun AccountCard(acc: Account, balance: Double, onRename: () -> Unit) {
    val palette = LocalHisaabPalette.current
    SurfaceCard(modifier = Modifier.fillMaxWidth(), shape = HisaabShapes.cardCompact) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    acc.name,
                    color = palette.onBackground,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(HisaabSpacing.xs))
                Eyebrow("${acc.kind} · ${acc.currency}")
            }
            // Design: negative balances → neg (red), everything else → plain text color, unsigned.
            MoneyText(
                amount = balance,
                signed = false,
                tone = MoneyTone.Plain,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (balance < 0.0) palette.negative else palette.onBackground,
                ),
            )
        }
        if (acc.kind == AccountKind.CARD) {
            CardSummaryRow(acc = acc)
        }
    }
}

private val SHORT_MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** Friendly due-date label matching the design "15 Jun 2026" (neo-settings.jsx:58). */
private fun LocalDate.toFriendlyDueLabel(): String =
    "$dayOfMonth ${SHORT_MONTHS[monthNumber - 1]} $year"

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
    val os = outstanding ?: return

    val available = if (cl != null) cl - os else null
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val nextDue = if (sd != null && dd != null) CardSummaryCalculator.nextDueDate(today, sd, dd) else null

    // Top border: hair-2, marginTop:14 paddingTop:14 (neo-settings.jsx:57).
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = palette.hair2, thickness = 1.dp)
    Spacer(Modifier.height(14.dp))

    val chipStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        ChipColumn("Outstanding") {
            // Always neg-colored; magnitude (a positive debt rendered red), no sign.
            MoneyText(amount = os, signed = false, tone = MoneyTone.Plain, style = chipStyle.copy(color = palette.negative))
        }
        if (available != null) {
            ChipColumn("Available") {
                MoneyText(amount = available, signed = false, tone = MoneyTone.Plain, style = chipStyle.copy(color = palette.positive))
            }
        }
        if (nextDue != null) {
            ChipColumn("Due") {
                Text(nextDue.toFriendlyDueLabel(), color = palette.muted, style = chipStyle)
            }
        }
    }
}

@Composable
private fun ChipColumn(label: String, value: @Composable () -> Unit) {
    Column {
        Eyebrow(label)
        Spacer(Modifier.height(HisaabSpacing.xs))
        value()
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
    var name by remember { mutableStateOf(current) }
    MidnightDialog(
        onDismiss = onDismiss,
        title = "Rename account",
        confirmLabel = "Save",
        onConfirm = { onSave(name.trim()) },
        confirmEnabled = name.isNotBlank() && name.trim() != current,
    ) {
        MidnightTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
