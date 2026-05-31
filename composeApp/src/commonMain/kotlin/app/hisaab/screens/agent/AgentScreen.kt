package app.hisaab.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.agent.AgentAvailability
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.SurfaceCard
import app.hisaab.domain.AgentRole
import kotlinx.serialization.json.JsonObject

@Composable
fun AgentScreen(vm: AgentViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsState()
    AgentScreenContent(
        state = state,
        onInput = vm::onInputChange,
        onSend = vm::onSend,
        onMicTap = vm::onMicTap,
        onNewChat = vm::onNewChat,
        onConsent = vm::onConsent,
        onToggleInclude = vm::onToggleInclude,
        onEditWrite = vm::onEditWrite,
        onApply = vm::onApply,
        onClose = onClose,
    )
}

/**
 * Stateless chat UI. Drives AgentUiState without touching any VM or DB, so an instrumented
 * test can render this directly with synthetic state (see AgentScreenTest).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreenContent(
    state: AgentUiState,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
    onNewChat: () -> Unit,
    onConsent: () -> Unit,
    onToggleInclude: (Int) -> Unit,
    onEditWrite: (Int, JsonObject) -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive or inFlight changes.
    LaunchedEffect(state.messages.size, state.inFlight) {
        val lastIndex = state.messages.size - 1 + (if (state.inFlight) 1 else 0)
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .imePadding(),
    ) {
        // ── Header ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.background)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) {
                Text("Close", color = palette.muted, fontSize = 15.sp)
            }

            Spacer(Modifier.width(4.dp))

            SparkleOrb(size = 36)

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Assistant",
                    color = palette.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "On-device · private",
                    color = palette.muted,
                    fontSize = 12.sp,
                )
            }

            TextButton(onClick = onNewChat) {
                Text("New", color = palette.accent, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Gate ────────────────────────────────────────────────────────────────
        // NeedsConsent → opt-in dialog with disclosure.
        // Unavailable (no provider/key) → inline card pointing to Settings.
        when (val gate = state.gate) {
            is AgentAvailability.NeedsConsent -> {
                AgentConsentDialog(
                    onConsent = onConsent,
                    onDismiss = onClose,
                )
            }
            is AgentAvailability.Unavailable -> {
                SurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("agent_gate_banner"),
                ) {
                    Text(
                        text = gate.reason,
                        color = palette.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Enable in Settings to continue.",
                        color = palette.muted,
                        fontSize = 12.sp,
                    )
                }
            }
            AgentAvailability.Ready, null -> Unit
        }

        // ── Error banner ────────────────────────────────────────────────────────
        state.error?.let { err ->
            Text(
                text = err,
                color = palette.negative,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .testTag("agent_error"),
            )
        }

        // ── Confirmation banner ─────────────────────────────────────────────────
        state.confirmation?.let { msg ->
            Text(
                text = msg,
                color = palette.positive,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }

        // ── Message thread ──────────────────────────────────────────────────────
        val gateReady = state.gate == AgentAvailability.Ready || state.gate == null
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Empty state — only when there are no messages and the gate is ready/null.
            if (state.messages.isEmpty() && gateReady) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SparkleOrb(size = 64)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Ask about your money",
                            color = palette.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "Your data stays on device — nothing is shared.",
                            color = palette.muted,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        SuggestionChips(
                            suggestions = listOf(
                                "Set a food budget",
                                "I paid 500 for lunch",
                                "Move 1000 to cash",
                            ),
                            onPick = { text ->
                                onInput(text)
                                onSend()
                            },
                        )
                    }
                }
            }

            items(state.messages, key = { it.id }) { msg ->
                val isUser = msg.role == AgentRole.USER
                val bubbleShape = if (isUser) {
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 6.dp,
                    )
                } else {
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 6.dp,
                        bottomEnd = 20.dp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .clip(bubbleShape)
                            .background(if (isUser) palette.accent else palette.surface)
                            .border(
                                width = if (isUser) 0.dp else 1.dp,
                                color = if (isUser) palette.accent else palette.hair,
                                shape = bubbleShape,
                            )
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                            .testTag("agent_msg_${msg.id}"),
                    ) {
                        Text(
                            text = msg.content,
                            color = if (isUser) palette.onAccent else palette.onBackground,
                            fontSize = 15.5.sp,
                        )
                    }
                }
            }

            // inFlight indicator — TypingDots thinking bubble.
            if (state.inFlight) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        val inflightShape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 6.dp,
                            bottomEnd = 20.dp,
                        )
                        Box(
                            modifier = Modifier
                                .clip(inflightShape)
                                .background(palette.surface)
                                .border(1.dp, palette.hair, inflightShape)
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                                .testTag("agent_inflight"),
                        ) {
                            TypingDots()
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Review section — full ReviewCard introduced in T7.
            if (state.review.isNotEmpty()) {
                item {
                    ReviewCard(
                        writes = state.review,
                        included = state.reviewIncluded,
                        onToggle = onToggleInclude,
                        onEdit = onEditWrite,
                        onApply = onApply,
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        // ── Listening indicator ─────────────────────────────────────────────────
        if (state.listening) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ListeningEqualizer()
                Text(
                    text = "Listening…",
                    color = palette.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ── Input bar — pinned at bottom ────────────────────────────────────────
        val inputDisabled = state.gate is AgentAvailability.Unavailable
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.glass)
                .border(width = 1.dp, color = palette.hair)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInput,
                placeholder = {
                    Text("Ask something…", color = palette.muted)
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("agent_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = if (state.listening) palette.accent else palette.hair,
                    focusedTextColor = palette.onBackground,
                    unfocusedTextColor = palette.onBackground,
                    cursorColor = palette.accent,
                    disabledBorderColor = palette.hair,
                ),
                maxLines = 4,
                shape = HisaabShapes.pill,
                enabled = !inputDisabled,
            )

            Spacer(Modifier.width(8.dp))

            // Send button — enabled when input is non-blank and not inFlight.
            val canSend = state.input.isNotBlank() && !state.inFlight && !inputDisabled
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (state.input.isNotBlank()) palette.accent else palette.surfaceRaised)
                    .testTag("agent_send"),
            ) {
                Text(
                    text = "↑",
                    color = if (state.input.isNotBlank()) palette.onAccent else palette.faint,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(6.dp))

            // Mic button — push-to-talk on-device STT; tinted while listening.
            IconButton(
                onClick = onMicTap,
                enabled = state.voiceAvailable && !inputDisabled,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (state.listening) palette.accent else palette.surface)
                    .testTag("agent_mic")
                    .semantics {
                        contentDescription = when {
                            state.listening -> "Listening — tap to stop"
                            state.voiceAvailable -> "Voice input — tap to dictate"
                            else -> "Voice input unavailable"
                        }
                    },
            ) {
                Text(
                    text = "🎙",
                    fontSize = 20.sp,
                    color = if (state.listening) palette.onAccent else palette.onBackground,
                )
            }
        }
    }
}
