package app.hisaab.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.GlyphChip
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.MoneyTone
import app.hisaab.design.components.SurfaceCard
import app.hisaab.design.components.categoryHue
import app.hisaab.domain.AccountKind
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.TxnKind
import app.hisaab.screens.onboarding.CaptureOptInCard
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
            insightRepo = container.insightRepository,
            loadDisplayName = {
                container.databaseOrNull()?.hisaabDatabaseQueries?.getUserProfile()
                    ?.executeAsOneOrNull()?.display_name
            },
        )
    }
    val net by viewModel.todayNet.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val captureOptInSeen by viewModel.captureOptInSeen.collectAsState()
    val captureEnabled by viewModel.captureEnabled.collectAsState()
    val totalBalance by viewModel.totalBalance.collectAsState()
    val accountCards by viewModel.accountCards.collectAsState()
    val monthNet by viewModel.monthNet.collectAsState()
    val displayName by viewModel.displayName.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Header: greeting + name on the left; search + gradient avatar on the right (neo.jsx:82-91).
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greetingFor(),
                    color = palette.muted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                )
                Text(
                    displayName ?: "Welcome",
                    color = palette.onBackground,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp).clip(CircleShape)
                        .background(palette.glass).border(1.dp, palette.hair, CircleShape)
                        .clickable { onReview() },
                    contentAlignment = Alignment.Center,
                ) { HisaabIcon("search", tint = palette.onBackground, size = 19.dp) }
                Box(
                    modifier = Modifier
                        .size(42.dp).clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(categoryHue("violet"), categoryHue("blue")),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initialsOf(displayName),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // TOTAL BALANCE hero with a lime radial glow (neo.jsx:93-112).
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-40).dp)
                    .size(160.dp)
                    .background(Brush.radialGradient(listOf(palette.accentSoft, Color.Transparent)), CircleShape),
            )
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Eyebrow("Total balance")
            Spacer(Modifier.height(10.dp))
            MoneyText(
                amount = totalBalance,
                tone = MoneyTone.Plain,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 50.sp, color = palette.onBackground),
            )
            Spacer(Modifier.height(14.dp))
            DeltaPill(monthNet = monthNet, palette = palette)
            Spacer(Modifier.height(18.dp))

            // In / out bar — fed by today's net (neo.jsx:101-111)
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
        }

        // Horizontal account strip of mini-cards (neo.jsx:114-126).
        if (accountCards.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            LazyRow(
                // Full-bleed to the screen edges: cancel the parent's 20.dp gutter on both sides via a
                // layout widener (Modifier.padding cannot be negative — it throws). contentPadding keeps
                // the first/last card aligned to the 20.dp gutter.
                modifier = Modifier.layout { measurable, constraints ->
                    val extra = 40.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(maxWidth = constraints.maxWidth + extra),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(-20.dp.roundToPx(), 0) }
                },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                items(accountCards, key = { it.id }) { card -> AccountStripCard(card = card) }
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

/** Pos/neg pill "+… this month" under the hero (neo.jsx:98-100). Hidden when net is flat. */
@Composable
private fun DeltaPill(monthNet: Double, palette: HisaabColors.Palette) {
    if (monthNet == 0.0) return
    val positive = monthNet > 0.0
    val fg = if (positive) palette.positive else palette.negative
    val bg = if (positive) palette.positiveSoft else palette.negativeSoft
    Row(
        modifier = Modifier
            .clip(HisaabShapes.pill)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        HisaabIcon(if (positive) "arrow-up" else "chevron-down", tint = fg, size = 13.dp)
        MoneyText(
            amount = monthNet,
            tone = MoneyTone.Plain,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg),
        )
        Text("this month", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** A 150dp account mini-card: hued icon tile + KIND eyebrow, name, mono balance (neo.jsx:114-126). */
@Composable
private fun AccountStripCard(card: AccountCard) {
    val palette = LocalHisaabPalette.current
    val hue = accountHue(card.kind)
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(hue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { HisaabIcon(iconForKind(card.kind), tint = hue, size = 15.dp) }
            Eyebrow(card.kind.name)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            card.name,
            color = palette.muted,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        MoneyText(
            amount = card.balance,
            tone = MoneyTone.Plain,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (card.balance < 0.0) palette.negative else palette.onBackground,
            ),
        )
    }
}

private fun iconForKind(kind: AccountKind): String = when (kind) {
    AccountKind.CASH -> "wallet"
    AccountKind.CARD -> "month"
    AccountKind.BANK -> "month"
    AccountKind.MFS -> "month"
    AccountKind.GOAL -> "today"
}

private fun accountHue(kind: AccountKind): Color = when (kind) {
    AccountKind.CASH -> categoryHue("amber")
    AccountKind.BANK -> categoryHue("blue")
    AccountKind.CARD -> categoryHue("violet")
    AccountKind.MFS -> categoryHue("rose")
    AccountKind.GOAL -> categoryHue("teal")
}

private fun initialsOf(name: String?): String {
    val parts = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private fun greetingFor(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
