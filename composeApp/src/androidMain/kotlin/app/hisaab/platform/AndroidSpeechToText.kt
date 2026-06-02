package app.hisaab.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-device STT via Android [SpeechRecognizer] (M4-5). Prefers offline recognition; streams
 * partial hypotheses and a final result, then stops. Fail-closed: missing RECORD_AUDIO surfaces
 * [SpeechEvent.PermissionDenied]; any recognizer error surfaces [SpeechEvent.Failed] (degrade to typing).
 *
 * SpeechRecognizer is main-thread-only, so the producer block (create / start / destroy) runs on
 * [Dispatchers.Main] via flowOn. Needs a [FragmentActivity] so [requestPermission] can prompt for
 * RECORD_AUDIO up front (the launcher is registered at construction, before the activity is STARTED).
 */
class AndroidSpeechToText(private val activity: FragmentActivity) : SpeechToText {

    private val permissionResult = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1)
    private val permissionMutex = Mutex()
    private val permissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionResult.tryEmit(granted)
        }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override suspend fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(activity) && hasMicPermission()

    /** Up-front RECORD_AUDIO request (Android has a single mic permission). No-op when already granted. */
    override suspend fun requestPermission(): Boolean = permissionMutex.withLock {
        if (hasMicPermission()) return@withLock true
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        permissionResult.first()
    }

    override fun listen(localeTag: String): Flow<SpeechEvent> = callbackFlow {
        if (!hasMicPermission()) {
            trySend(SpeechEvent.PermissionDenied)
            close()
            return@callbackFlow
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Push-to-talk tolerance: don't end the utterance on short pauses while the mic is held.
            // (Best-effort extras; honoured by the Google recognizer, ignored elsewhere.)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) trySend(SpeechEvent.Final(text))
                else trySend(SpeechEvent.Failed("No speech recognized"))
                close()
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    trySend(SpeechEvent.PermissionDenied)
                } else {
                    trySend(SpeechEvent.Failed("Recognizer error $error"))
                }
                close()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main)
}
