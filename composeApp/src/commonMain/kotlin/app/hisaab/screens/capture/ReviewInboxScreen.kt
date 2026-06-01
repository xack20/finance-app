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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import app.hisaab.design.components.PrimaryButton
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review", color = palette.onBackground) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Close", color = palette.muted) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
        ) {
            if (pending.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing to review.\nAuto-captured transactions land here when Hisaab isn't sure.",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
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
                                    .clip(RoundedCornerShape(14.dp))
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
) {
    var rawExpanded by remember { mutableStateOf(false) }
    val c = rc.candidate
    val (sign, color) = when (c.direction) {
        Direction.CREDIT -> "+" to palette.positive
        Direction.DEBIT -> "−" to palette.negative
        null -> "" to palette.muted
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.rule, RoundedCornerShape(14.dp))
            .padding(18.dp),
    ) {
        Text(
            if (c.amount != null) "$sign৳${c.amount.toInt()}" else "Amount unknown",
            color = color,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append(rc.candidate.proposedMerchant ?: rc.candidate.sender)
                rc.categoryName?.let { append(" · "); append(it) }
            },
            color = palette.onBackground,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                append(c.sender)
                append(" · ")
                append(rc.accountName ?: "No account")
                c.confidence?.let { append(" · "); append("${(it * 100).toInt()}% sure") }
            },
            color = palette.muted,
            fontSize = 12.sp,
        )

        c.parseError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Needs a manual fix: $err",
                color = palette.negative,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (rawExpanded) c.rawBody else "Show original SMS ▾",
            color = palette.muted,
            fontSize = 12.sp,
            modifier = Modifier.clickable { rawExpanded = !rawExpanded },
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = "Confirm",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
            TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Text("Edit", color = palette.accent)
            }
        }
    }
}
