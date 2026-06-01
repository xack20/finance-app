package app.hisaab.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.GlyphChip
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MidnightDialog
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.NeoTopBar
import app.hisaab.design.components.SurfaceCard
import app.hisaab.design.components.categoryHue
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

    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        NeoTopBar(
            title = "Transaction",
            onBack = onDone,
            rightLabel = "Delete",
            rightColor = palette.negative,
            onRight = { showDeleteDialog = true },
        )
        val d = display
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Transaction not found", color = palette.muted)
            }
        } else {
            // INCOME/LEND/SETTLEMENT credit the user (+), everything else debits (−).
            val credit = d.row.kind in setOf(TxnKind.INCOME, TxnKind.LEND, TxnKind.SETTLEMENT)
            val signedAmount = if (credit) d.row.amount else -d.row.amount
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HisaabSpacing.gutter),
            ) {
                Spacer(Modifier.height(8.dp))
                // Hero: category glyph chip + signed mono amount (grouped thousands).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GlyphChip(iconName = d.categoryIcon, hue = categoryHue(d.categoryColor), size = 48)
                    // Design (neo-detail.jsx:17): credit → --pos (green), debit → --text (white), never red.
                    MoneyText(
                        amount = signedAmount,
                        signed = true,
                        decimals = 0,
                        color = if (credit) palette.positive else palette.onBackground,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 44.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    d.row.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = palette.muted,
                    fontSize = 14.5.sp,
                )
                Spacer(Modifier.height(24.dp))

                // Detail rows inside a card.
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    DetailRow("Merchant", d.merchantName ?: "—", palette)
                    DetailRow("Category", d.categoryName ?: "—", palette)
                    DetailRow("Account", d.accountName, palette)
                    DetailRow("When", formatTs(d.row.ts), palette)
                    d.row.notes?.let { DetailRow("Notes", it, palette) }
                }

                provenance?.let { prov ->
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        ProvenanceBlock(prov, palette)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteDialog) {
        MidnightDialog(
            onDismiss = { showDeleteDialog = false },
            title = "Delete this transaction?",
            body = "This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete(onDone)
            },
            destructive = true,
        )
    }
}

@Composable
private fun ProvenanceBlock(
    candidate: app.hisaab.domain.CandidateTransaction,
    palette: HisaabColors.Palette,
) {
    var rawExpanded by remember { mutableStateOf(false) }
    // Captured eyebrow row with a lime sparkle.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HisaabIcon("sparkle", tint = palette.accent, size = 16.dp)
        Eyebrow("Captured", color = palette.accent)
    }
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable { rawExpanded = !rawExpanded },
    ) {
        Text(
            if (rawExpanded) "Hide original SMS" else "Show original SMS",
            color = palette.accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
        )
        HisaabIcon(if (rawExpanded) "chevron-down" else "chevron-right", tint = palette.accent, size = 16.dp)
    }
    if (rawExpanded) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.background)
                .padding(12.dp),
        ) {
            Text(
                candidate.rawBody,
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
            )
        }
    }
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
        Text(value, color = palette.onBackground, fontWeight = FontWeight.Medium)
    }
}

private fun formatTs(ms: Long): String {
    val instant = Instant.fromEpochMilliseconds(ms)
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val pad: (Int) -> String = { if (it < 10) "0$it" else "$it" }
    return "${ldt.year}-${pad(ldt.monthNumber)}-${pad(ldt.dayOfMonth)} ${pad(ldt.hour)}:${pad(ldt.minute)}"
}
