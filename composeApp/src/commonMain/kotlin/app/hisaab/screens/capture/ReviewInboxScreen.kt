package app.hisaab.screens.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.GlyphChip
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.NeoTopBar
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.categoryHue
import app.hisaab.domain.Direction

/** Default auto-post threshold (matches CaptureConfig default 0.85) for the bulk action. */
private const val HIGH_CONFIDENCE_THRESHOLD = 0.85

@Composable
fun ReviewInboxScreen(
    onBack: () -> Unit,
    onEdit: (candidateId: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = remember {
        ReviewInboxViewModel(
            inboxRepo = container.captureInboxRepository,
            accountRepo = container.accountRepository,
            categoryRepo = container.categoryRepository,
            confirmCandidate = { id -> container.confirmCandidate(id) },
        )
    }
    val pending by viewModel.pending.collectAsState()
    ReviewInboxContent(
        pending = pending,
        onBack = onBack,
        onConfirm = viewModel::confirm,
        onConfirmAllHighConfidence = { viewModel.confirmAllHighConfidence(HIGH_CONFIDENCE_THRESHOLD) },
        onDismiss = viewModel::dismiss,
        onEdit = onEdit,
    )
}

/**
 * The testable inbox body. Takes plain data + callbacks (no AppContainer / VM), so a Compose UI
 * test can render the REAL screen with a fake pending list and assert Confirm / swipe-dismiss /
 * Edit invoke the right callbacks (see ReviewInboxScreenTest).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewInboxContent(
    pending: List<ReviewCandidate>,
    onBack: () -> Unit,
    onConfirm: (candidateId: String) -> Unit,
    onConfirmAllHighConfidence: () -> Unit,
    onDismiss: (candidateId: String) -> Unit,
    onEdit: (candidateId: String) -> Unit,
) {
    val palette = LocalHisaabPalette.current
    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        NeoTopBar(title = "Review", onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            if (pending.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // pos-soft check tile.
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(palette.positiveSoft),
                            contentAlignment = Alignment.Center,
                        ) { HisaabIcon("check", tint = palette.positive, size = 30.dp, strokeWidth = 2.2f) }
                        Text("Nothing to review", color = palette.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(
                            "Captured transactions land here when Hisaab isn't sure.",
                            color = palette.muted,
                            fontSize = 13.sp,
                        )
                    }
                }
                return@Column
            }

            val anyHigh = pending.any { (it.candidate.confidence ?: 0.0) >= HIGH_CONFIDENCE_THRESHOLD }
            if (anyHigh) {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Confirm all high-confidence",
                    onClick = onConfirmAllHighConfidence,
                )
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(pending, key = { it.candidate.id }) { rc ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { target ->
                            if (target == SwipeToDismissBoxValue.EndToStart) {
                                onDismiss(rc.candidate.id)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(palette.negative)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text("Dismiss", color = palette.background, fontWeight = FontWeight.SemiBold)
                            }
                        },
                    ) {
                        CandidateCard(
                            rc = rc,
                            palette = palette,
                            onConfirm = { onConfirm(rc.candidate.id) },
                            onEdit = { onEdit(rc.candidate.id) },
                            onDismiss = { onDismiss(rc.candidate.id) },
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    rc: ReviewCandidate,
    palette: HisaabColors.Palette,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var rawExpanded by remember { mutableStateOf(false) }
    val c = rc.candidate
    val signedAmount = when (c.direction) {
        Direction.CREDIT -> c.amount ?: 0.0
        Direction.DEBIT -> -(c.amount ?: 0.0)
        null -> c.amount ?: 0.0
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(22.dp))
            .padding(20.dp),
    ) {
        // Hero: category glyph + signed mono amount (grouped).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlyphChip(iconName = null, hue = categoryHue(null), size = 40)
            if (c.amount != null) {
                MoneyText(
                    amount = signedAmount,
                    signed = true,
                    decimals = 0,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
                )
            } else {
                Text("Amount unknown", color = palette.muted, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append(rc.candidate.proposedMerchant ?: rc.candidate.sender)
                rc.categoryName?.let { append(" · "); append(it) }
            },
            color = palette.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${c.sender} · ${rc.accountName ?: "No account"}",
                color = palette.muted,
                fontSize = 13.sp,
            )
            // Confidence pill — pos when ≥85%, amber otherwise.
            c.confidence?.let { conf ->
                val high = conf >= HIGH_CONFIDENCE_THRESHOLD
                val amber = HisaabColors.categoryHues.getValue("amber")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background((if (high) palette.positive else amber).copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "${(conf * 100).toInt()}% sure",
                        color = if (high) palette.positive else amber,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        c.parseError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text("Needs a manual fix: $err", color = palette.negative, fontSize = 12.sp)
        }

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
                    c.rawBody,
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = "Confirm",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
            GlassButton(
                text = "Edit",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Dismiss", color = palette.negative, fontWeight = FontWeight.SemiBold)
        }
    }
}
