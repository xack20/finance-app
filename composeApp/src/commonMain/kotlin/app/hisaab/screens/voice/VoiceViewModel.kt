package app.hisaab.screens.voice

import app.hisaab.agent.AgentAvailability
import app.hisaab.agent.AgentRuntime
import app.hisaab.agent.ProposedWrite
import app.hisaab.platform.NoSpeechToText
import app.hisaab.platform.SpeechEvent
import app.hisaab.platform.SpeechToText
import app.hisaab.screens.agent.userMessageFor
import kotlinx.coroutines.CancellationException
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
 * Recognizer language for voice capture. The on-device speech recognizer must be told which
 * language to decode — a Bangla command decoded as English ([localeTag] "en-…") comes out garbled,
 * which is the #1 cause of bad Bangla recognition. Default is Bangla; the user can toggle to English.
 */
enum class VoiceLang(val localeTag: String, val label: String) {
    BN("bn-BD", "বাংলা"),
    EN("en-US", "EN"),
}

/**
 * Voice-first capture (Neo `VoiceFlow`, neo-voice.jsx). Push-to-talk: hold the mic, speak, release —
 * the spoken command is recognized by [SpeechToText], parsed into proposed writes by the existing
 * [AgentRuntime] (same engine as the Assistant), shown as a draft, and applied. No new parser/save.
 */
sealed interface VoiceStep {
    /** Idle/holding — ready to capture the command (push-to-talk). */
    data object Listen : VoiceStep

    /** The command was submitted and the runtime is parsing it. */
    data object Parsing : VoiceStep

    /** A query with no writes — show the assistant's textual [message] (e.g. "You've spent ৳4,200…"). */
    data class Answer(val message: String) : VoiceStep

    /** Parsed into [writes] ready to save; [message] is the runtime's one-line summary, if any. */
    data class Draft(val writes: List<ProposedWrite>, val message: String) : VoiceStep

    /** The draft was applied successfully; [writes] is shown as the saved summary. */
    data class Done(val writes: List<ProposedWrite>) : VoiceStep
}

data class VoiceUiState(
    val step: VoiceStep = VoiceStep.Listen,
    val listening: Boolean = false,
    /** Recognizer language — drives the STT locale (bn-BD / en-US). */
    val language: VoiceLang = VoiceLang.BN,
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
    /** Default recognizer language; Bangla is the primary use here. */
    initialLanguage: VoiceLang = VoiceLang.BN,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(VoiceUiState(language = initialLanguage))
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var listenJob: Job? = null

    init {
        scope.launch {
            val avail = runtime.availability()
            if (avail != AgentAvailability.Ready) _state.update { it.copy(gate = avail) }
        }
        scope.launch {
            _state.update { it.copy(sttAvailable = speechToText.isAvailable()) }
        }
        // Push-to-talk: do NOT auto-start listening — the user holds the mic to begin.
    }

    fun setLanguage(lang: VoiceLang) {
        if (_state.value.listening) stopListening()
        _state.update { it.copy(language = lang) }
    }

    /** Press-and-hold start: open the recognizer in the selected language. */
    fun onHoldStart() = startListening()

    /** Release: stop the recognizer and parse whatever was captured. */
    fun onHoldEnd() = submit()

    private fun startListening() {
        if (_state.value.listening) return
        listenJob?.cancel()
        listenJob = scope.launch {
            if (!speechToText.isAvailable()) {
                _state.update { it.copy(sttAvailable = false) }
                return@launch
            }
            _state.update { it.copy(listening = true, error = null, transcript = "") }
            try {
                speechToText.listen(_state.value.language.localeTag).collect { ev ->
                    when (ev) {
                        is SpeechEvent.Partial -> _state.update { it.copy(transcript = ev.text) }
                        // Hold-to-talk: capture the final transcript but DON'T auto-submit — releasing submits.
                        is SpeechEvent.Final -> _state.update { it.copy(transcript = ev.text, listening = false) }
                        SpeechEvent.PermissionDenied ->
                            _state.update { it.copy(listening = false, sttAvailable = false, error = "Allow microphone access in Settings to use voice.") }
                        is SpeechEvent.Failed ->
                            _state.update { it.copy(listening = false, error = "Couldn't hear that — hold and try again, or type.") }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Never let a recognizer/audio failure crash the app — degrade to typing.
                _state.update { it.copy(listening = false, error = "Couldn't start the microphone — type instead.") }
            }
            _state.update { it.copy(listening = false) }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        _state.update { it.copy(listening = false) }
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
                .onSuccess { _state.update { it.copy(step = VoiceStep.Done(draft.writes)) } }
                .onFailure { _state.update { it.copy(error = "Couldn't save — rolled back.") } }
        }
    }

    /** Discard the draft/answer and return to the hold-to-speak state for a fresh command. */
    fun redo() {
        _state.update { it.copy(step = VoiceStep.Listen, transcript = "", typed = "", error = null) }
    }
}
