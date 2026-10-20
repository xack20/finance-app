package app.hisaab.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Captures raw 16 kHz mono PCM via [AudioRecord] for cloud transcription ([CloudTranscriber]). Used
 * on the voice path because Android's on-device [AndroidSpeechToText] doesn't reliably do Bangla.
 * On [stop] the captured PCM is wrapped in a WAV header ([pcm16leToWav]) and returned in memory —
 * nothing is written to disk.
 *
 * Push-to-talk: [start] on hold spins a read loop on a background thread, [stop] on release joins it
 * and returns the clip. Requires RECORD_AUDIO (declared in the manifest, requested up front by
 * [AndroidSpeechToText.requestPermission]); [isAvailable] reflects the current grant.
 */
class AndroidAudioRecorder(private val context: Context) : AudioRecorder {

    private val sampleRate = 16_000
    private val channels = 1

    @Volatile private var recording = false
    private var record: AudioRecord? = null
    private var readThread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun isAvailable(): Boolean = hasMicPermission()

    @SuppressLint("MissingPermission") // guarded by hasMicPermission() above
    override fun start() {
        if (recording || !hasMicPermission()) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = if (minBuf > 0) minBuf * 2 else sampleRate * 2
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize,
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }
        record = rec
        synchronized(pcm) { pcm.reset() }
        recording = true
        rec.startRecording()
        readThread = thread(start = true, name = "voice-rec") {
            val chunk = ByteArray(bufSize)
            // Guarded so a teardown release() that stops/releases the recorder mid-read() can't
            // crash this thread with IllegalStateException — it just exits.
            runCatching {
                while (recording) {
                    val n = rec.read(chunk, 0, chunk.size)
                    if (n > 0) synchronized(pcm) { pcm.write(chunk, 0, n) }
                }
            }
        }
    }

    override suspend fun stop(): ByteArray? = withContext(Dispatchers.IO) {
        if (!recording) return@withContext null
        recording = false
        val rec = record
        record = null
        runCatching { rec?.stop() } // stop BEFORE join so the blocking read() returns immediately
        readThread?.join(1_000)
        readThread = null
        rec?.release()
        val bytes = synchronized(pcm) { pcm.toByteArray() }
        if (bytes.isEmpty()) null else pcm16leToWav(bytes, sampleRate, channels)
    }

    override fun release() {
        if (!recording && record == null) return
        recording = false
        val rec = record
        record = null
        // The read loop is runCatching-guarded, so stopping/releasing under it is safe without a
        // blocking join (teardown runs on the main thread — keep it cheap). The thread exits on its
        // next loop check or read() error.
        runCatching { rec?.stop() }
        rec?.release()
        readThread = null
        synchronized(pcm) { pcm.reset() }
    }
}
