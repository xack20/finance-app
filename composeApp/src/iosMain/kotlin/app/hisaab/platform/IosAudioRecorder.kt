@file:OptIn(ExperimentalForeignApi::class)

package app.hisaab.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

/**
 * Records mic audio to a temporary 16 kHz mono PCM **WAV** file via [AVAudioRecorder], then returns
 * the bytes for cloud transcription ([CloudTranscriber]). Used on the voice path because Apple's
 * on-device Speech recognizer doesn't support Bangla on most iPhones.
 *
 * AVAudioRecorder with `kAudioFormatLinearPCM` + a `.wav` URL writes a valid WAV container directly,
 * so no manual header is needed here (unlike Android's raw [AudioRecord]). The temp file lives under
 * NSTemporaryDirectory with a random NSUUID name, is read back into memory on [stop], and deleted
 * immediately — nothing financial is persisted. Mic permission is requested up front by
 * [IosSpeechToText.requestPermission]; [isAvailable] reflects the current grant.
 *
 * Contract: never throws. AVAudioRecorder/session setup can raise ObjC NSExceptions on device when
 * the mic route isn't ready; everything is guarded so a failure degrades to a null clip.
 */
class IosAudioRecorder : AudioRecorder {

    private var recorder: AVAudioRecorder? = null
    private var fileUrl: NSURL? = null

    override suspend fun isAvailable(): Boolean =
        AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

    override fun start() {
        if (recorder?.recording == true) return
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryRecord, error = null)
            session.setActive(true, error = null)

            val path = NSTemporaryDirectory() + NSUUID().UUIDString() + ".wav"
            val url = NSURL.fileURLWithPath(path)
            fileUrl = url

            // 16 kHz mono signed 16-bit little-endian PCM — small clips, plenty for speech.
            val settings: Map<Any?, *> = mapOf<Any?, Any?>(
                AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatLinearPCM),
                AVSampleRateKey to NSNumber(double = 16000.0),
                AVNumberOfChannelsKey to NSNumber(int = 1),
                AVLinearPCMBitDepthKey to NSNumber(int = 16),
                AVLinearPCMIsBigEndianKey to NSNumber(bool = false),
                AVLinearPCMIsFloatKey to NSNumber(bool = false),
            )
            val rec = AVAudioRecorder(url, settings, null)
            recorder = rec
            rec.prepareToRecord()
            rec.record()
        } catch (e: Throwable) {
            recorder = null
            fileUrl = null
        }
    }

    override suspend fun stop(): ByteArray? {
        val rec = recorder ?: return null
        val url = fileUrl
        recorder = null
        fileUrl = null
        return try {
            rec.stop()
            AVAudioSession.sharedInstance().setActive(false, error = null)
            val data = url?.let { NSData.dataWithContentsOfURL(it) }
            url?.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
            val bytes = data?.toByteArray()
            if (bytes == null || bytes.isEmpty()) null else bytes
        } catch (e: Throwable) {
            null
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        val out = ByteArray(size)
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
        return out
    }
}
