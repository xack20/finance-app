package app.hisaab.platform

/**
 * Records mic audio for cloud transcription (the on-device recognizer can't do Bangla on most
 * devices). Push-to-talk: [start] on hold, [stop] on release returns a 16-bit PCM **WAV** clip
 * (the format cloud STT accepts most reliably) — or null if nothing was captured. Needs the same
 * RECORD_AUDIO / microphone permission as [SpeechToText].
 */
interface AudioRecorder {
    /** True if recording is usable (mic permission granted + capability present). */
    suspend fun isAvailable(): Boolean

    /** Begin capturing mic audio. No-op if already recording. */
    fun start()

    /** Stop and return the clip as 16-bit PCM WAV bytes, or null if empty / failed. */
    suspend fun stop(): ByteArray?
}

/** Fallback when recording isn't available (wasm, or no mic). */
object NoAudioRecorder : AudioRecorder {
    override suspend fun isAvailable(): Boolean = false
    override fun start() {}
    override suspend fun stop(): ByteArray? = null
}

/**
 * Transcribes a recorded clip to text via a cloud model (used when on-device STT can't handle the
 * language, e.g. Bangla). The parse step is unchanged — this only replaces the listen step.
 */
interface CloudTranscriber {
    /** True if a cloud transcription provider + key is configured. */
    suspend fun isAvailable(): Boolean

    /**
     * Transcribe [wav] (16-bit PCM WAV) to verbatim text. [languageHint] is a BCP-47-ish hint
     * ("bn", "en", …) the model uses to bias decoding; the transcript keeps the spoken language.
     */
    suspend fun transcribe(wav: ByteArray, languageHint: String): String
}

/** Fallback when no cloud transcriber is configured. */
object NoCloudTranscriber : CloudTranscriber {
    override suspend fun isAvailable(): Boolean = false
    override suspend fun transcribe(wav: ByteArray, languageHint: String): String = ""
}

/**
 * Wrap little-endian 16-bit [pcm] samples in a canonical 44-byte WAV header. Cross-platform so both
 * recorders emit the same `audio/wav` bytes regardless of how they captured the PCM.
 */
fun pcm16leToWav(pcm: ByteArray, sampleRate: Int, channels: Int): ByteArray {
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataLen = pcm.size
    val header = ByteArray(44)
    fun le32(off: Int, v: Int) {
        header[off] = (v and 0xFF).toByte()
        header[off + 1] = ((v shr 8) and 0xFF).toByte()
        header[off + 2] = ((v shr 16) and 0xFF).toByte()
        header[off + 3] = ((v shr 24) and 0xFF).toByte()
    }
    fun le16(off: Int, v: Int) {
        header[off] = (v and 0xFF).toByte()
        header[off + 1] = ((v shr 8) and 0xFF).toByte()
    }
    fun ascii(off: Int, s: String) = s.forEachIndexed { i, c -> header[off + i] = c.code.toByte() }
    ascii(0, "RIFF"); le32(4, 36 + dataLen); ascii(8, "WAVE")
    ascii(12, "fmt "); le32(16, 16); le16(20, 1); le16(22, channels)
    le32(24, sampleRate); le32(28, byteRate); le16(32, blockAlign); le16(34, bitsPerSample)
    ascii(36, "data"); le32(40, dataLen)
    return header + pcm
}
