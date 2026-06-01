package app.hisaab.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.GradientAvatar
import app.hisaab.design.components.HRadio
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MidnightSheet
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.MoneyTone
import app.hisaab.design.components.NeoTopBar
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SurfaceCard
import app.hisaab.design.components.midnightOutlinedColors
import kotlin.math.abs
import app.hisaab.domain.Account
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.LendBorrowRow
import app.hisaab.domain.LendBorrowStatus
import app.hisaab.util.toTaka

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

    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        NeoTopBar(title = detail?.person?.name ?: "Person", onBack = onBack)
        val d = detail
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                // Balance card
                item {
                    Spacer(Modifier.height(12.dp))
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GradientAvatar(name = d.person.name, size = 54)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val balanceLabel = when {
                                    d.balance > 0 -> "They owe you"
                                    d.balance < 0 -> "You owe them"
                                    else -> "Settled"
                                }
                                Eyebrow(text = balanceLabel)
                                // Magnitude only, always plain text color (the label conveys direction) —
                                // matches the design's Money, which is always --text. tone=Plain avoids
                                // Auto coloring abs() as a positive (which rendered debts green).
                                MoneyText(
                                    amount = abs(d.balance),
                                    signed = false,
                                    decimals = 0,
                                    tone = MoneyTone.Plain,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.onBackground,
                                    ),
                                )
                            }
                        }
                        d.person.contactRef?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = palette.muted, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Eyebrow(text = "History")
                    Spacer(Modifier.height(8.dp))
                }

                if (d.records.isEmpty()) {
                    item {
                        Text("No records yet.", color = palette.muted, fontSize = 13.sp)
                    }
                } else {
                    items(d.records, key = { it.id }) { row ->
                        Spacer(Modifier.height(10.dp))
                        LendBorrowRowItem(
                            row = row,
                            onSettle = { settleRecord = row },
                        )
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }

    val record = settleRecord
    if (record != null) {
        SettleSheet(
            record = record,
            accounts = accounts,
            onConfirm = { amount, accountId ->
                viewModel.settle(record.id, amount, accountId)
                settleRecord = null
            },
            onDismiss = { settleRecord = null },
        )
    }
}

@Composable
private fun LendBorrowRowItem(
    row: LendBorrowRow,
    onSettle: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon tile
            val isLent = row.direction == LendBorrowDirection.LENT
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isLent) palette.positiveSoft else palette.negativeSoft),
                contentAlignment = Alignment.Center,
            ) {
                HisaabIcon(
                    name = if (isLent) "lend" else "borrow",
                    tint = if (isLent) palette.positive else palette.negative,
                    size = 20.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val directionLabel = if (isLent) "Lent" else "Borrowed"
                Text(
                    text = "$directionLabel ${row.amount.toTaka()}",
                    color = palette.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val noteAndStatus = buildString {
                    row.purpose?.takeIf { it.isNotBlank() }?.let { append(it) }
                    if (isNotEmpty()) append(" · ")
                    append(row.status.name)
                }
                Text(
                    text = noteAndStatus,
                    color = palette.muted,
                    fontSize = 13.sp,
                )
            }
            if (row.status != LendBorrowStatus.SETTLED) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(HisaabShapes.pill)
                        .background(palette.accentSoft)
                        .clickable { onSettle() }
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                ) {
                    Text(
                        text = "Settle",
                        color = palette.accent,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleSheet(
    record: LendBorrowRow,
    accounts: List<Account>,
    onConfirm: (Double, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    var amount by remember { mutableStateOf(record.amount.toInt().toString()) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    MidnightSheet(
        onDismiss = onDismiss,
        title = "Settle",
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Amount") },
            prefix = { Text("৳") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = HisaabShapes.field,
            colors = midnightOutlinedColors(),
        )
        Spacer(Modifier.height(16.dp))
        Eyebrow(text = "Into account")
        Spacer(Modifier.height(8.dp))
        accounts.forEach { acc ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HRadio(
                    selected = acc.id == accountId,
                    onClick = { accountId = acc.id },
                )
                Spacer(Modifier.width(10.dp))
                Text(acc.name, color = palette.onBackground)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Settle",
            onClick = {
                val a = amount.toDoubleOrNull()
                if (a != null && a > 0 && accountId.isNotBlank()) {
                    onConfirm(a, accountId)
                }
            },
            enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && accountId.isNotBlank(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel", color = palette.muted)
        }
    }
}
