package app.hisaab.screens.agent

import app.hisaab.agent.AgentAvailability
import app.hisaab.agent.AgentRuntime
import app.hisaab.agent.AppliedSummary
import app.hisaab.agent.ChatMessage
import app.hisaab.agent.ProposedWrite
import app.hisaab.agent.Role
import app.hisaab.data.ConversationRepository
import app.hisaab.domain.AgentMessage
import app.hisaab.domain.AgentRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class AgentUiState(
    val conversationId: String? = null,
    val messages: List<AgentMessage> = emptyList(),
    val input: String = "",
    val inFlight: Boolean = false,
    val review: List<ProposedWrite> = emptyList(),
    val reviewIncluded: Set<Int> = emptySet(),
    val assistantMessageId: String? = null,
    val gate: AgentAvailability? = null,
    val error: String? = null,
    val confirmation: String? = null,
)

class AgentViewModel(
    private val conversationRepo: ConversationRepository,
    private val runtime: AgentRuntime,
    private val setConsent: suspend () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private var messageCollectorJob: Job? = null

    init {
        scope.launch {
            val cid = conversationRepo.latestConversationId()
                ?: conversationRepo.createConversation()
            _state.update { it.copy(conversationId = cid) }
            collectMessages(cid)
        }
    }

    private fun collectMessages(conversationId: String) {
        messageCollectorJob?.cancel()
        messageCollectorJob = scope.launch {
            conversationRepo.observeMessages(conversationId).collect { msgs ->
                _state.update { it.copy(messages = msgs) }
            }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun onMicTap() {
        // mic disabled in M4-6 — no-op
    }

    fun onConsent() {
        scope.launch {
            setConsent()
            _state.update { it.copy(gate = null) }
        }
    }

    fun onToggleInclude(i: Int) {
        _state.update { current ->
            val included = current.reviewIncluded.toMutableSet()
            if (i in included) included.remove(i) else included.add(i)
            current.copy(reviewIncluded = included)
        }
    }

    fun onEditWrite(i: Int, args: JsonObject) {
        _state.update { current ->
            val updated = current.review.toMutableList()
            if (i in updated.indices) {
                updated[i] = ProposedWrite(updated[i].tool, args)
            }
            current.copy(review = updated)
        }
    }

    fun onNewChat() {
        scope.launch {
            val cid = conversationRepo.createConversation()
            _state.update { it.copy(
                conversationId = cid,
                review = emptyList(),
                reviewIncluded = emptySet(),
                assistantMessageId = null,
                error = null,
                confirmation = null,
            ) }
            collectMessages(cid)
        }
    }

    fun onSend() {
        val text = _state.value.input.trim()
        if (text.isBlank()) return
        _state.update { it.copy(input = "") }

        scope.launch {
            val avail = runtime.availability()
            when (avail) {
                is AgentAvailability.Unavailable -> {
                    _state.update { it.copy(gate = avail) }
                }
                AgentAvailability.Ready -> {
                    val cid = _state.value.conversationId ?: return@launch
                    conversationRepo.appendUserMessage(cid, text)
                    _state.update { it.copy(inFlight = true, error = null, confirmation = null) }

                    val history = _state.value.messages.map { msg ->
                        ChatMessage(
                            role = if (msg.role == AgentRole.ASSISTANT) Role.ASSISTANT else Role.USER,
                            content = msg.content,
                        )
                    }

                    runCatching { runtime.run(history, text) }
                        .onSuccess { res ->
                            val mid = conversationRepo.appendAssistantMessage(
                                cid,
                                res.finalMessage,
                                res.proposedWrites,
                                null,
                            )
                            _state.update { it.copy(
                                review = res.proposedWrites,
                                reviewIncluded = res.proposedWrites.indices.toSet(),
                                assistantMessageId = mid,
                                inFlight = false,
                            ) }
                        }
                        .onFailure {
                            _state.update { it.copy(
                                error = "Something went wrong — nothing was saved.",
                                inFlight = false,
                            ) }
                        }
                }
            }
        }
    }

    fun onApply() {
        scope.launch {
            val current = _state.value
            val included = current.review.filterIndexed { i, _ -> i in current.reviewIncluded }

            runCatching { runtime.apply(included) }
                .onSuccess { summary ->
                    current.assistantMessageId?.let { mid ->
                        conversationRepo.recordApplied(mid, summary)
                    }
                    _state.update { it.copy(
                        review = emptyList(),
                        reviewIncluded = emptySet(),
                        confirmation = "Saved.",
                    ) }
                }
                .onFailure {
                    _state.update { it.copy(
                        error = "Couldn't save — rolled back.",
                    ) }
                }
        }
    }
}
