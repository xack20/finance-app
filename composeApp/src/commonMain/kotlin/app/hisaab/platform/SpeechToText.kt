package app.hisaab.platform

/** Placeholder for on-device speech-to-text (real impl in M4-5). */
interface SpeechToText {
    suspend fun isAvailable(): Boolean
}

/** No-op stub used until M4-5 wires platform STT. */
object NoSpeechToText : SpeechToText {
    override suspend fun isAvailable(): Boolean = false
}
