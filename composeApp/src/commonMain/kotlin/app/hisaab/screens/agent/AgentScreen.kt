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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import app.hisaab.design.LocalHisaabPalette
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistant", color = palette.onBackground) },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("Close", color = palette.muted)
                    }
                },
                actions = {
                    TextButton(onClick = onNewChat) {
                        Text("New chat", color = palette.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Gate — shown when the feature is not ready.
            // NeedsConsent → opt-in dialog with disclosure.
            // Unavailable (no provider/key) → inline banner pointing to Settings.
            when (val gate = state.gate) {
                is AgentAvailability.NeedsConsent -> {
                    AgentConsentDialog(
                        onConsent = onConsent,
                        onDismiss = onClose,
                    )
                }
                is AgentAvailability.Unavailable -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.surface)
                            .border(1.dp, palette.rule)
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                            .testTag("agent_gate_banner"),
                    ) {
                        Column {
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
                }
                AgentAvailability.Ready, null -> Unit
            }

            // Error line.
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

            // Confirmation line.
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

            // Message thread.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                items(state.messages, key = { it.id }) { msg ->
                    val isUser = msg.role == AgentRole.USER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isUser) 14.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 14.dp,
                                    )
                                )
                                .background(if (isUser) palette.accent else palette.surface)
                                .border(
                                    width = if (isUser) 0.dp else 1.dp,
                                    color = if (isUser) palette.accent else palette.rule,
                                    shape = RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isUser) 14.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 14.dp,
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("agent_msg_${msg.id}"),
                        ) {
                            Text(
                                text = msg.content,
                                color = if (isUser) palette.background else palette.onBackground,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                // inFlight indicator — "…" thinking bubble.
                if (state.inFlight) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 14.dp,
                                            topEnd = 14.dp,
                                            bottomEnd = 14.dp,
                                            bottomStart = 4.dp,
                                        )
                                    )
                                    .background(palette.surface)
                                    .border(
                                        1.dp,
                                        palette.rule,
                                        RoundedCornerShape(
                                            topStart = 14.dp,
                                            topEnd = 14.dp,
                                            bottomEnd = 14.dp,
                                            bottomStart = 4.dp,
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("agent_inflight"),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = palette.muted,
                                    strokeWidth = 2.dp,
                                )
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

            // Input bar — pinned at bottom.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.background)
                    .border(width = 1.dp, color = palette.rule)
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
                        .testTag("agent_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.rule,
                        focusedTextColor = palette.onBackground,
                        unfocusedTextColor = palette.onBackground,
                        cursorColor = palette.accent,
                    ),
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.width(8.dp))

                // Send button — enabled when input is non-blank and not inFlight.
                val canSend = state.input.isNotBlank() && !state.inFlight
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) palette.accent else palette.rule)
                        .testTag("agent_send"),
                ) {
                    Text(
                        text = "↑",
                        color = if (canSend) palette.background else palette.muted,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Mic button (M4-5) — push-to-talk on-device STT; enabled when voice is available,
                // tinted while listening. Degrades silently to typing when unavailable.
                IconButton(
                    onClick = onMicTap,
                    enabled = state.voiceAvailable,
                    modifier = Modifier
                        .size(44.dp)
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
                        text = "🎤",
                        fontSize = 18.sp,
                        color = if (state.listening) palette.accent else palette.onBackground,
                    )
                }
            }
        }
    }
}
