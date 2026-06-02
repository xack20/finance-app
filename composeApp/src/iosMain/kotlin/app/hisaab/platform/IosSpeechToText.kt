@file:OptIn(ExperimentalForeignApi::class)

package app.hisaab.platform

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioInputNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
import platform.Speech.SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusNotDetermined

/**
 * On-device STT via Apple Speech (SFSpeechRecognizer) + AVAudioEngine (M4-5). Streams partial +
 * final transcripts; emits [SpeechEvent.PermissionDenied] when speech/mic authorization is missing
 * and [SpeechEvent.Failed] on recognizer/audio errors (degrade to typing). Requires
 * NSSpeechRecognitionUsageDescription + NSMicrophoneUsageDescription in Info.plist.
 *
 * Contract: never throws into the collector. The AVAudioEngine APIs (notably installTapOnBus /
 * startAndReturnError / the record session activation) raise ObjC NSExceptions on a real device
 * when the mic route/format isn't ready — Kotlin/Native surfaces those as catchable Throwables, so
 * the whole setup is guarded and any failure degrades to [SpeechEvent.Failed] instead of crashing.
 */
class IosSpeechToText : SpeechToText {

    override suspend fun isAvailable(): Boolean {
        val recognizer = SFSpeechRecognizer() ?: return false
        val status = SFSpeechRecognizer.authorizationStatus()
        return recognizer.available &&
            (status == SFSpeechRecognizerAuthorizationStatusAuthorized ||
                status == SFSpeechRecognizerAuthorizationStatusNotDetermined)
    }

    /** Request Speech recognition + Microphone up front — iOS shows the two prompts in sequence. */
    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        SFSpeechRecognizer.requestAuthorization { speechStatus ->
            val speechOk = speechStatus == SFSpeechRecognizerAuthorizationStatusAuthorized
            AVAudioSession.sharedInstance().requestRecordPermission { micOk ->
                if (cont.isActive) cont.resume(speechOk && micOk)
            }
        }
    }

    /** First *available* SFSpeechRecognizer for the requested language, trying regional variants. */
    private fun recognizerForLanguage(requested: String): SFSpeechRecognizer? {
        val lang = requested.substringBefore('-').substringBefore('_').lowercase()
        val candidates = buildList {
            add(requested)
            when (lang) {
                "bn" -> { add("bn-IN"); add("bn-BD"); add("bn") }
                "en" -> { add("en-US"); add("en-IN"); add("en") }
                else -> add(lang)
            }
        }.distinct()
        for (tag in candidates) {
            val r = runCatching { SFSpeechRecognizer(NSLocale(tag)) }.getOrNull()
            if (r != null && r.available) return r
        }
        return null
    }

    override fun listen(localeTag: String): Flow<SpeechEvent> = callbackFlow {
        if (SFSpeechRecognizer.authorizationStatus() != SFSpeechRecognizerAuthorizationStatusAuthorized) {
            // Trigger the system prompt for next time; this attempt fails closed.
            SFSpeechRecognizer.requestAuthorization { }
            trySend(SpeechEvent.PermissionDenied)
            close()
            return@callbackFlow
        }

        var engine: AVAudioEngine? = null
        var input: AVAudioInputNode? = null
        var task: SFSpeechRecognitionTask? = null
        var request: SFSpeechAudioBufferRecognitionRequest? = null

        try {
            // Resolve a recognizer for the requested language across regional variants (e.g. bn-BD →
            // bn-IN → bn). Apple's Speech locale set is limited; if the language isn't supported the
            // initializer returns nil (or throws for some tags), so this is inside the guard. We do NOT
            // silently fall back to the device locale — recognizing Bangla speech as English is worse
            // than a clear message, and it tells the user whether their iPhone supports Bangla at all.
            val recognizer = recognizerForLanguage(localeTag)
            if (recognizer == null) {
                val lang = localeTag.substringBefore('-').substringBefore('_')
                trySend(SpeechEvent.Failed("On-device voice doesn't support “$lang” on this iPhone — switch to EN or type."))
                close()
                return@callbackFlow
            }
            if (!recognizer.available) {
                trySend(SpeechEvent.Failed("Voice model isn't ready — switch language or type."))
                close()
                return@callbackFlow
            }
            // Activate the record session BEFORE reading the input format / installing the tap — on a
            // real device the input node's format is invalid (0 Hz) until the session is active, and
            // installTapOnBus(..., invalidFormat) raises an NSException. (Simulator reports a valid
            // default format regardless, which is why this only crashed on the iPhone.)
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryRecord, error = null)
            session.setActive(true, error = null)

            val eng = AVAudioEngine().also { engine = it }
            val req = SFSpeechAudioBufferRecognitionRequest().apply { shouldReportPartialResults = true }
            request = req

            task = recognizer.recognitionTaskWithRequest(req) { result, error ->
                if (result != null) {
                    val text = result.bestTranscription.formattedString
                    if (result.final) { trySend(SpeechEvent.Final(text)); close() }
                    else trySend(SpeechEvent.Partial(text))
                }
                if (error != null) { trySend(SpeechEvent.Failed(error.localizedDescription)); close() }
            }

            val inputNode = eng.inputNode.also { input = it }
            val format = inputNode.outputFormatForBus(0u)
            if (format.sampleRate == 0.0 || format.channelCount == 0u) {
                trySend(SpeechEvent.Failed("Microphone unavailable — check mic permission."))
                close()
                return@callbackFlow
            }
            inputNode.installTapOnBus(0u, 1024u, format) { buffer, _ ->
                buffer?.let { req.appendAudioPCMBuffer(it) }
            }
            eng.prepare()
            eng.startAndReturnError(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Any audio/recognizer setup failure (ObjC NSException incl.) degrades to typing.
            trySend(SpeechEvent.Failed(e.message ?: "Couldn't start the microphone"))
            runCatching {
                engine?.stop()
                input?.removeTapOnBus(0u)
                task?.cancel()
            }
            close()
            return@callbackFlow
        }

        awaitClose {
            runCatching {
                engine?.stop()
                input?.removeTapOnBus(0u)
                request?.endAudio()
                task?.cancel()
                AVAudioSession.sharedInstance().setActive(false, error = null)
            }
        }
    }
}
