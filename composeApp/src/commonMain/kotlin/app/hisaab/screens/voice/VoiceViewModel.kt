package app.hisaab.screens.voice

import app.hisaab.agent.AgentAvailability
import app.hisaab.agent.AgentRuntime
import app.hisaab.agent.ProposedWrite
import app.hisaab.platform.NoSpeechToText
import app.hisaab.platform.SpeechEvent
import app.hisaab.platform.SpeechToText
import app.hisaab.screens.agent.userMessageFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Voice-first capture (Neo `VoiceFlow`, neo-voice.jsx). A single spoken command is recognized by
 * [SpeechToText], parsed into proposed writes by the existing [AgentRuntime] (same engine as the
 * Assistant), shown as a draft, and applied. Mirrors the assistant pipeline — no new parser/save.
 */
sealed interface VoiceStep {
    /** Mic is open (or idle, ready to tap) and capturing the command. */
    data object Listen : VoiceStep

    /** The command was submitted and the runtime is parsing it. */
    data object Parsing : VoiceStep

    /** A query with no writes — show the assistant's textual [message] (e.g. "You've spent ৳4,200…"). */
    data class Answer(val message: String) : VoiceStep

    /** Parsed into [writes] ready to save; [message] is the runtime's one-line summary, if any. */
    data class Draft(val writes: List<ProposedWrite>, val message: String) : VoiceStep

    /** The draft was applied successfully. */
    data object Done : VoiceStep
}

data class VoiceUiState(
    val step: VoiceStep = VoiceStep.Listen,
    val listening: Boolean = false,
    /** The recognized (or typed) command, shown live. */
    val transcript: String = "",
    /** Type-fallback field contents. */
    val typed: String = "",
    val sttAvailable: Boolean = true,
    /** Non-null when the agent isn't usable (needs consent / no provider) — UI shows the gate. */
    val gate: AgentAvailability? = null,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = transcript.isNotBlank() || typed.isNotBlank()
}

class VoiceViewModel(
    private val runtime: AgentRuntime,
    private val speechToText: SpeechToText = NoSpeechToText,
    /** Design recognizer locale (Bangla/English code-switching works best with en-IN). */
    private val locale: String = "en-IN",
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var listenJob: Job? = null

    init {
        scope.launch {
            val avail = runtime.availability()
            if (avail != AgentAvailability.Ready) _state.update { it.copy(gate = avail) }
        }
        scope.launch {
            val ok = speechToText.isAvailable()
            _state.update { it.copy(sttAvailable = ok) }
            if (ok && _state.value.gate == null) startListening()
        }
    }

    fun startListening() {
        if (_state.value.listening) return
        listenJob?.cancel()
        listenJob = scope.launch {
            if (!speechToText.isAvailable()) {
                _state.update { it.copy(sttAvailable = false) }
                return@launch
            }
            _state.update { it.copy(listening = true, error = null, transcript = "") }
            speechToText.listen(locale).collect { ev ->
                when (ev) {
                    is SpeechEvent.Partial -> _state.update { it.copy(transcript = ev.text) }
                    is SpeechEvent.Final -> {
                        _state.update { it.copy(transcript = ev.text, listening = false) }
                        submit(ev.text)
                    }
                    SpeechEvent.PermissionDenied ->
                        _state.update { it.copy(listening = false, sttAvailable = false, error = "Allow microphone access in Settings to use voice.") }
                    is SpeechEvent.Failed ->
                        _state.update { it.copy(listening = false, error = "Couldn't hear that — try again or type.") }
                }
            }
            _state.update { it.copy(listening = false) }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        _state.update { it.copy(listening = false) }
    }

    fun onMicTap() {
        if (_state.value.listening) stopListening() else startListening()
    }

    fun onTyped(text: String) {
        _state.update { it.copy(typed = text) }
    }

    /** Parse the spoken/typed command. [textArg] overrides (e.g. an example chip). */
    fun submit(textArg: String? = null) {
        val text = (textArg ?: _state.value.transcript.ifBlank { _state.value.typed }).trim()
        if (text.isBlank()) return
        stopListening()
        _state.update { it.copy(transcript = text, step = VoiceStep.Parsing, error = null) }
        scope.launch {
            when (val avail = runtime.availability()) {
                AgentAvailability.Ready -> {
                    runCatching { runtime.run(emptyList(), text) }
                        .onSuccess { res ->
                            _state.update {
                                it.copy(
                                    step = if (res.proposedWrites.isNotEmpty()) {
                                        VoiceStep.Draft(res.proposedWrites, res.finalMessage)
                                    } else {
                                        VoiceStep.Answer(res.finalMessage)
                                    },
                                )
                            }
                        }
                        .onFailure { t ->
                            _state.update { it.copy(step = VoiceStep.Listen, error = userMessageFor(t)) }
                        }
                }
                else -> _state.update { it.copy(step = VoiceStep.Listen, gate = avail) }
            }
        }
    }

    /** Apply the current [VoiceStep.Draft] writes via the same committer the Assistant uses. */
    fun save() {
        val draft = _state.value.step as? VoiceStep.Draft ?: return
        _state.update { it.copy(error = null) }
        scope.launch {
            runCatching { runtime.apply(draft.writes) }
                .onSuccess { _state.update { it.copy(step = VoiceStep.Done) } }
                .onFailure { _state.update { it.copy(error = "Couldn't save — rolled back.") } }
        }
    }

    /** Discard the draft/answer and listen for a fresh command. */
    fun redo() {
        _state.update { it.copy(step = VoiceStep.Listen, transcript = "", typed = "", error = null) }
        startListening()
    }
}
