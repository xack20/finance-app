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
import app.hisaab.platform.AudioRecorder
import app.hisaab.platform.CloudTranscriber
import app.hisaab.platform.NoAudioRecorder
import app.hisaab.platform.NoCloudTranscriber
import app.hisaab.platform.NoSpeechToText
import app.hisaab.platform.SpeechEvent
import app.hisaab.platform.SpeechToText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    /** Cloud path only: the recorded clip is being transcribed by Gemini. */
    val transcribing: Boolean = false,
)

class AgentViewModel(
    private val conversationRepo: ConversationRepository,
    private val runtime: AgentRuntime,
    private val setConsent: suspend () -> Unit,
    private val speechToText: SpeechToText = NoSpeechToText,
    /** Cloud recorder + transcriber: when a Gemini key is set, the mic goes record→Gemini (Bangla). */
    private val audioRecorder: AudioRecorder = NoAudioRecorder,
    private val cloudTranscriber: CloudTranscriber = NoCloudTranscriber,
    private val locale: String = "en-US",
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private var messageCollectorJob: Job? = null
    private var listenJob: Job? = null

    /** True when mic capture goes via record→Gemini (Bangla-capable) instead of the on-device recognizer. */
    private var usingCloud: Boolean = false

    init {
        scope.launch {
            val cid = conversationRepo.latestConversationId()
                ?: conversationRepo.createConversation()
            _state.update { it.copy(conversationId = cid) }
            collectMessages(cid)
        }
        scope.launch {
            // Prefer cloud STT when a Gemini key is set — on-device recognizers can't do Bangla.
            usingCloud = cloudTranscriber.isAvailable()
            // Request mic (+ speech) up front so the user isn't prompted mid-recording; both
            // recognizers' requestPermission() also grant the mic the cloud recorder needs.
            val granted = speechToText.requestPermission()
            val canCapture =
                if (usingCloud) granted && audioRecorder.isAvailable() else granted && speechToText.isAvailable()
            _state.update { it.copy(voiceAvailable = canCapture) }
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
     * Tap-to-talk toggle. Prefers cloud STT (record → Gemini, Bangla-capable); falls back to the
     * on-device recognizer when no Gemini key. First tap starts; second tap stops and (cloud)
     * transcribes the clip into the input field, where the user can edit before sending. Degrades to
     * typing when voice is unavailable or permission is denied.
     */
    fun onMicTap() {
        val s = _state.value
        if (s.transcribing) return // ignore taps while a clip is being transcribed
        if (s.listening) {
            if (usingCloud) {
                stopAndTranscribe()
            } else {
                listenJob?.cancel()
                _state.update { it.copy(listening = false) }
            }
            return
        }
        if (usingCloud) startRecording() else startListening()
    }

    /** Cloud tap-to-talk start: begin mic capture (no recognizer). Second tap → [stopAndTranscribe]. */
    private fun startRecording() {
        _state.update { it.copy(listening = true, error = null) }
        // start() is synchronous and self-guarded (never throws); failure degrades to typing.
        runCatching { audioRecorder.start() }.onFailure {
            _state.update { it.copy(listening = false, error = "Couldn't start the microphone — type instead.") }
        }
    }

    /** Cloud tap-to-talk stop: transcribe via Gemini (auto-detect language) into the input field. */
    private fun stopAndTranscribe() {
        _state.update { it.copy(listening = false, transcribing = true, error = null) }
        scope.launch {
            val wav = runCatching { audioRecorder.stop() }.getOrNull()
            if (wav == null || wav.isEmpty()) {
                _state.update { it.copy(transcribing = false, error = "Didn't catch that — try again or type.") }
                return@launch
            }
            runCatching { cloudTranscriber.transcribe(wav, "") } // "" → Gemini auto-detects the language
                .onSuccess { text ->
                    _state.update {
                        if (text.isBlank()) {
                            it.copy(transcribing = false, error = "Didn't catch that — try again or type.")
                        } else {
                            // Don't clobber text the user typed while recording — append to it.
                            val merged = if (it.input.isBlank()) text else "${it.input.trimEnd()} $text"
                            it.copy(transcribing = false, input = merged)
                        }
                    }
                }
                .onFailure { t -> _state.update { it.copy(transcribing = false, error = userMessageFor(t)) } }
        }
    }

    /** On-device recognizer fallback (no Gemini key): stream partials into the input until Final/stop. */
    private fun startListening() {
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

    /**
     * Release the mic and cancel background work (message collector, in-flight turns) when the
     * screen is disposed. Without this, closing the chat mid-recording would leave the recorder
     * running and the collector job alive, since the runtime scope isn't tied to the composition.
     * Wired from AgentScreen.
     */
    fun dispose() {
        listenJob?.cancel()
        messageCollectorJob?.cancel()
        audioRecorder.release()
        scope.cancel()
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
        is LlmError.Refused -> "The model declined this request — try rephrasing it."
        null -> "Something went wrong — nothing was saved."
    }
