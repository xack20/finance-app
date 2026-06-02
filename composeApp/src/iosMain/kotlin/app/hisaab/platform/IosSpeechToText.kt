@file:OptIn(ExperimentalForeignApi::class)

package app.hisaab.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
import platform.Speech.SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusNotDetermined

/**
 * On-device STT via Apple Speech (SFSpeechRecognizer) + AVAudioEngine (M4-5). Streams partial +
 * final transcripts; emits [SpeechEvent.PermissionDenied] when speech/mic authorization is missing
 * and [SpeechEvent.Failed] on recognizer/audio errors (degrade to typing). Requires
 * NSSpeechRecognitionUsageDescription + NSMicrophoneUsageDescription in Info.plist.
 */
class IosSpeechToText : SpeechToText {

    override suspend fun isAvailable(): Boolean {
        val recognizer = SFSpeechRecognizer() ?: return false
        val status = SFSpeechRecognizer.authorizationStatus()
        return recognizer.available &&
            (status == SFSpeechRecognizerAuthorizationStatusAuthorized ||
                status == SFSpeechRecognizerAuthorizationStatusNotDetermined)
    }

    override fun listen(localeTag: String): Flow<SpeechEvent> = callbackFlow {
        if (SFSpeechRecognizer.authorizationStatus() != SFSpeechRecognizerAuthorizationStatusAuthorized) {
            // Trigger the system prompt for next time; this attempt fails closed.
            SFSpeechRecognizer.requestAuthorization { }
            trySend(SpeechEvent.PermissionDenied)
            close()
            return@callbackFlow
        }

        val recognizer = SFSpeechRecognizer(NSLocale(localeTag)) ?: SFSpeechRecognizer()
        if (recognizer == null || !recognizer.available) {
            trySend(SpeechEvent.Failed("Speech recognizer unavailable"))
            close(); return@callbackFlow
        }

        // Activate the record audio session BEFORE reading the input-node format / installing the tap.
        // On a real device the input node reports an invalid format (0 Hz / 0 channels) until the
        // session is active for recording, and installTapOnBus(..., invalidFormat) throws an
        // NSException ("required condition is false: IsFormatSampleRateAndChannelCountValid") that
        // terminates the app. The simulator reports a valid default format regardless, which is why
        // this only crashed on the iPhone, not in the simulator.
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setActive(true, error = null)

        val engine = AVAudioEngine()
        val request = SFSpeechAudioBufferRecognitionRequest().apply { shouldReportPartialResults = true }

        val task = recognizer.recognitionTaskWithRequest(request) { result, error ->
            if (result != null) {
                val text = result.bestTranscription.formattedString
                if (result.final) { trySend(SpeechEvent.Final(text)); close() }
                else trySend(SpeechEvent.Partial(text))
            }
            if (error != null) { trySend(SpeechEvent.Failed(error.localizedDescription)); close() }
        }

        val input = engine.inputNode
        val format = input.outputFormatForBus(0u)
        if (format.sampleRate == 0.0) {
            // Session/route not ready — fail closed rather than crash on an invalid tap format.
            trySend(SpeechEvent.Failed("Microphone unavailable"))
            close(); return@callbackFlow
        }
        input.installTapOnBus(0u, 1024u, format) { buffer, _ ->
            buffer?.let { request.appendAudioPCMBuffer(it) }
        }
        engine.prepare()
        engine.startAndReturnError(null)

        awaitClose {
            engine.stop()
            input.removeTapOnBus(0u)
            request.endAudio()
            task?.cancel()
        }
    }
}
