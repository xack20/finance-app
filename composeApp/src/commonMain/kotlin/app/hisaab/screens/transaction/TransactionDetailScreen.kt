package app.hisaab.screens.transaction

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.ParsedBy
import app.hisaab.domain.TxnKind
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(txnId: String, onDone: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember {
        TransactionDetailViewModel(
            txnId = txnId,
            txnRepo = container.transactionRepository,
            accountRepo = container.accountRepository,
            categoryRepo = container.categoryRepository,
            merchantRepo = container.merchantRepository,
            inboxRepo = container.captureInboxRepository,
        )
    }
    val display by viewModel.row.collectAsState()
    val provenance by viewModel.provenance.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction", color = palette.onBackground) },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Back", color = palette.muted) }
                },
                actions = {
                    TextButton(onClick = { showDeleteDialog = true }) {
                        Text("Delete", color = palette.negative)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        val d = display
        if (d == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Transaction not found", color = palette.muted)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .padding(padding)
                    .padding(horizontal = 22.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                val (sign, color) = when (d.row.kind) {
                    TxnKind.INCOME, TxnKind.LEND, TxnKind.SETTLEMENT -> "+" to palette.positive
                    else -> "−" to palette.negative
                }
                Text(
                    "$sign৳${d.row.amount.toInt()}",
                    color = color,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    d.row.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = palette.muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(24.dp))
                DetailRow("Merchant", d.merchantName ?: "—", palette)
                DetailRow("Category", d.categoryName ?: "—", palette)
                DetailRow("Account", d.accountName, palette)
                DetailRow("When", formatTs(d.row.ts), palette)
                d.row.notes?.let { DetailRow("Notes", it, palette) }

                provenance?.let { prov ->
                    Spacer(Modifier.height(24.dp))
                    ProvenanceBlock(prov, palette)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDone)
                }) { Text("Delete", color = palette.negative) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
            text = { Text("Delete this transaction? This can't be undone.") },
        )
    }
}

@Composable
private fun ProvenanceBlock(
    candidate: app.hisaab.domain.CandidateTransaction,
    palette: HisaabColors.Palette,
) {
    var rawExpanded by remember { mutableStateOf(false) }
    Text(
        "CAPTURED",
        color = palette.accent,
        letterSpacing = 2.sp,
        fontSize = 11.sp,
    )
    Spacer(Modifier.height(8.dp))
    val engine = when (candidate.parsedBy) {
        ParsedBy.TEMPLATE -> "Template"
        ParsedBy.ON_DEVICE -> "On-device"
        ParsedBy.CLOUD_CLAUDE -> "Cloud · Claude"
        ParsedBy.CLOUD_GEMINI -> "Cloud · Gemini"
        ParsedBy.CLOUD_OPENAI -> "Cloud · OpenAI"
        null -> "Unknown"
    }
    DetailRow("Parsed by", engine, palette)
    candidate.model?.let { DetailRow("Model", it, palette) }
    candidate.confidence?.let { DetailRow("Confidence", "${(it * 100).toInt()}%", palette) }
    DetailRow("From", candidate.sender, palette)
    Spacer(Modifier.height(10.dp))
    Text(
        if (rawExpanded) candidate.rawBody else "Show original SMS ▾",
        color = palette.muted,
        fontSize = 12.sp,
        modifier = Modifier.clickable { rawExpanded = !rawExpanded },
    )
}

@Composable
private fun DetailRow(label: String, value: String, palette: HisaabColors.Palette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.muted, modifier = Modifier.weight(1f))
        Text(value, color = palette.onBackground)
    }
    HorizontalDivider(color = palette.rule)
}

private fun formatTs(ms: Long): String {
    val instant = Instant.fromEpochMilliseconds(ms)
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val pad: (Int) -> String = { if (it < 10) "0$it" else "$it" }
    return "${ldt.year}-${pad(ldt.monthNumber)}-${pad(ldt.dayOfMonth)} ${pad(ldt.hour)}:${pad(ldt.minute)}"
}
