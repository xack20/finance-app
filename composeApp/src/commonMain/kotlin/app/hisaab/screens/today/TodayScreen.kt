package app.hisaab.screens.today

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.hisaab.design.components.GlyphChip
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.MoneyTone
import app.hisaab.design.components.SectionHeader
import app.hisaab.design.components.SurfaceCard
import app.hisaab.design.components.categoryHue
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.TxnKind
import app.hisaab.screens.onboarding.CaptureOptInCard

@Composable
fun TodayScreen(onTxnClick: (String) -> Unit, onReview: () -> Unit, onAutoCapture: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember {
        TodayViewModel(
            txnRepo = container.transactionRepository,
            accountRepo = container.accountRepository,
            categoryRepo = container.categoryRepository,
            merchantRepo = container.merchantRepository,
            inboxRepo = container.captureInboxRepository,
            loadOptInSeen = { container.secureStorage.loadString("capture_optin_seen") == "true" },
            saveOptInSeen = { container.secureStorage.storeString("capture_optin_seen", "true") },
            captureConfigRepo = container.captureConfigRepository,
            smsCapable = container.captureService.capabilities().contains(CaptureChannel.SMS),
        )
    }
    val net by viewModel.todayNet.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val captureOptInSeen by viewModel.captureOptInSeen.collectAsState()
    val captureEnabled by viewModel.captureEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Header row: "Today" title + review badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionHeader(title = "Today")
            ReviewBadge(count = pendingCount, palette = palette, onClick = onReview)
        }

        Spacer(Modifier.height(16.dp))

        // Hero net card
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Eyebrow("Net today")
            Spacer(Modifier.height(8.dp))
            MoneyText(
                amount = net.net,
                signed = true,
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(12.dp))

            // In / out bar
            val income = net.income
            val expense = net.expense
            val bothZero = income == 0.0 && expense == 0.0
            if (bothZero) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(HisaabShapes.pill)
                        .background(palette.backgroundInset),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(HisaabShapes.pill)
                        .background(palette.backgroundInset),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(income.toFloat().coerceAtLeast(0.0001f))
                            .fillMaxSize()
                            .background(palette.positive),
                    )
                    Box(
                        modifier = Modifier
                            .weight(expense.toFloat().coerceAtLeast(0.0001f))
                            .fillMaxSize()
                            .background(palette.negative),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // In / out label row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In", color = palette.positive, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    Spacer(Modifier.width(6.dp))
                    MoneyText(amount = net.income, tone = MoneyTone.Plain)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(amount = net.expense, tone = MoneyTone.Plain)
                    Spacer(Modifier.width(6.dp))
                    Text("Out", color = palette.negative, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Review banner — only shown when pendingCount > 0
        if (pendingCount > 0) {
            SurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.accentSoft, HisaabShapes.card)
                    .clickable { onReview() },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Solid-lime sparkle tile.
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(palette.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        HisaabIcon("sparkle", tint = palette.onAccent, size = 20.dp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (pendingCount == 1L) "1 transaction to review" else "$pendingCount transactions to review",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.onBackground,
                        )
                        Text(
                            "Auto-captured from SMS",
                            fontSize = 13.sp,
                            color = palette.muted,
                        )
                    }
                    HisaabIcon("chevron-right", tint = palette.accent, size = 20.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Capture opt-in card — unchanged conditions and call site
        if (!captureEnabled && !captureOptInSeen && viewModel.smsSupported) {
            CaptureOptInCard(
                onTurnOn = {
                    viewModel.dismissOptIn()
                    onAutoCapture()
                },
                onMaybeLater = {
                    viewModel.dismissOptIn()
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        // Transaction feed
        if (recent.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No entries yet.\nTap + to record your first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.muted,
                )
            }
        } else {
            LazyColumn {
                items(recent, key = { it.row.id }) { display ->
                    TxnRow(
                        display = display,
                        onClick = { onTxnClick(display.row.id) },
                    )
                    HorizontalDivider(color = palette.hair)
                }
            }
        }
    }
}

@Composable
private fun TxnRow(
    display: TransactionRowDisplay,
    onClick: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    // Sign logic preserved from original: INCOME/LEND/SETTLEMENT are positive, else negative.
    val signedAmount = when (display.row.kind) {
        TxnKind.INCOME, TxnKind.LEND, TxnKind.SETTLEMENT -> display.row.amount
        else -> -display.row.amount
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphChip(
            iconName = display.categoryIcon,
            hue = categoryHue(display.categoryColor),
            size = 40,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    display.merchantName ?: display.categoryName ?: "—",
                    color = palette.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                )
                // Auto-capture indicator — lime sparkle next to the merchant name.
                if (display.row.captureId != null) {
                    Spacer(Modifier.width(6.dp))
                    HisaabIcon("sparkle", tint = palette.accent, size = 13.dp)
                }
            }
            Text(
                buildString {
                    append(display.accountName)
                    if (!display.categoryName.isNullOrBlank() && display.merchantName != null) {
                        append(" · ")
                        append(display.categoryName)
                    }
                },
                fontSize = 13.sp,
                color = palette.muted,
            )
        }
        MoneyText(amount = signedAmount, signed = true)
    }
}
