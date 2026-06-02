package app.hisaab.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * On-device speech-to-text (M4-5). Platform actuals stream partial + final transcripts from the
 * mic; the agent screen feeds them into the text input (voice degrades to typing when unavailable).
 */
interface SpeechToText {
    /** True if on-device recognition is usable on this platform/device. */
    suspend fun isAvailable(): Boolean

    /**
     * Requests microphone (+ speech-recognition, where the platform separates them) permission up
     * front and returns true if granted. Call when the voice UI opens so the user isn't prompted
     * mid-capture. Safe to call when already granted (no prompt). Default = [isAvailable] (no prompt).
     */
    suspend fun requestPermission(): Boolean = isAvailable()

    /**
     * Listens on the mic and emits [SpeechEvent]s until the collecting coroutine is cancelled
     * (push-to-talk: collect while the mic is held, cancel on release / on [SpeechEvent.Final]).
     * Emits [SpeechEvent.PermissionDenied] when mic/speech permission is missing, or
     * [SpeechEvent.Failed] on a recognizer error. Never throws into the collector.
     */
    fun listen(localeTag: String): Flow<SpeechEvent>
}

sealed interface SpeechEvent {
    /** Interim hypothesis (the UI may show it as it updates). */
    data class Partial(val text: String) : SpeechEvent
    /** Final recognized text for the utterance; the recognizer then stops. */
    data class Final(val text: String) : SpeechEvent
    /** Mic / speech-recognition permission not granted — the UI should prompt to enable it. */
    data object PermissionDenied : SpeechEvent
    /** Recognizer error (no match, busy, network, etc.); degrade to typing. */
    data class Failed(val reason: String) : SpeechEvent
}

/** Fallback when on-device STT isn't available (wasm, or recognition unsupported). */
object NoSpeechToText : SpeechToText {
    override suspend fun isAvailable(): Boolean = false
    override fun listen(localeTag: String): Flow<SpeechEvent> =
        flowOf(SpeechEvent.Failed("Voice input isn't available on this device."))
}
