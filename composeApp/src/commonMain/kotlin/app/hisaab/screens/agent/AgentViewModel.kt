package app.hisaab.screens.agent

import app.hisaab.agent.AgentAvailability
import app.hisaab.agent.AgentRuntime
import app.hisaab.agent.AppliedSummary
import app.hisaab.agent.ChatMessage
import app.hisaab.agent.ProposedWrite
import app.hisaab.agent.Role
import app.hisaab.data.ConversationRepository
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import app.hisaab.domain.AgentMessage
import app.hisaab.domain.AgentRole
import app.hisaab.platform.NoSpeechToText
import app.hisaab.platform.SpeechEvent
import app.hisaab.platform.SpeechToText
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
    val voiceAvailable: Boolean = false,
    val listening: Boolean = false,
)

class AgentViewModel(
    private val conversationRepo: ConversationRepository,
    private val runtime: AgentRuntime,
    private val setConsent: suspend () -> Unit,
    private val speechToText: SpeechToText = NoSpeechToText,
    private val locale: String = "en-US",
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private var messageCollectorJob: Job? = null
    private var listenJob: Job? = null

    init {
        scope.launch {
            val cid = conversationRepo.latestConversationId()
                ?: conversationRepo.createConversation()
            _state.update { it.copy(conversationId = cid) }
            collectMessages(cid)
        }
        scope.launch {
            _state.update { it.copy(voiceAvailable = speechToText.isAvailable()) }
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

    /**
     * Push-to-talk toggle (M4-5). Starts on-device recognition and streams transcripts into the
     * input field; tapping again (or a Final result / error) stops. Degrades to typing when voice
     * is unavailable or permission is denied.
     */
    fun onMicTap() {
        if (_state.value.listening) {
            listenJob?.cancel()
            _state.update { it.copy(listening = false) }
            return
        }
        listenJob = scope.launch {
            if (!speechToText.isAvailable()) {
                _state.update { it.copy(error = "Voice input isn't available on this device.", voiceAvailable = false) }
                return@launch
            }
            _state.update { it.copy(listening = true, error = null) }
            speechToText.listen(locale).collect { ev ->
                when (ev) {
                    is SpeechEvent.Partial -> _state.update { it.copy(input = ev.text) }
                    is SpeechEvent.Final -> _state.update { it.copy(input = ev.text, listening = false) }
                    SpeechEvent.PermissionDenied ->
                        _state.update { it.copy(listening = false, error = "Allow microphone access in Settings to use voice.") }
                    is SpeechEvent.Failed ->
                        _state.update { it.copy(listening = false, error = "Couldn't hear that — try again or type.") }
                }
            }
            _state.update { it.copy(listening = false) }
        }
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
        // Clear any prior banner on every send path (incl. NeedsConsent / Unavailable), so a
        // stale error/confirmation can't linger or coexist with a new one.
        _state.update { it.copy(input = "", error = null, confirmation = null) }

        scope.launch {
            val avail = runtime.availability()
            when (avail) {
                AgentAvailability.NeedsConsent -> {
                    _state.update { it.copy(gate = avail) }
                }
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
                        .onFailure { t ->
                            _state.update { it.copy(
                                error = userMessageFor(t),
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
            // error/confirmation are a single mutually-exclusive status; clear before applying.
            _state.update { it.copy(error = null, confirmation = null) }

            runCatching { runtime.apply(included) }
                .onSuccess { summary ->
                    current.assistantMessageId?.let { mid ->
                        conversationRepo.recordApplied(mid, summary)
                    }
                    _state.update { it.copy(
                        review = emptyList(),
                        reviewIncluded = emptySet(),
                        confirmation = "Saved.",
                        error = null,
                    ) }
                }
                .onFailure {
                    _state.update { it.copy(
                        error = "Couldn't save — rolled back.",
                        confirmation = null,
                    ) }
                }
        }
    }

    /** Dismiss the transient error/confirmation banner (wired to the banner's dismiss action). */
    fun onDismissBanner() {
        _state.update { it.copy(error = null, confirmation = null) }
    }
}

/**
 * Map a failed turn to a specific, actionable user message (M4-7). Provider/transport failures
 * arrive as [LlmException] carrying an [LlmError]; anything else falls back to a generic line.
 */
internal fun userMessageFor(t: Throwable): String =
    when (val e = (t as? LlmException)?.error) {
        LlmError.InvalidKey -> "Your API key looks invalid — check it in Settings."
        LlmError.RateLimited -> "The model is busy (rate-limited) — try again in a moment."
        LlmError.Unavailable -> "The model provider is unavailable right now — try again later."
        is LlmError.Network -> "Network problem — check your connection and try again."
        is LlmError.ProviderError -> "The model returned an error (HTTP ${e.status}) — please try again."
        is LlmError.Decode -> "The assistant's reply couldn't be read — try rephrasing."
        null -> "Something went wrong — nothing was saved."
    }
