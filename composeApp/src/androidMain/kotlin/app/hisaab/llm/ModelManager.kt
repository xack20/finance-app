package app.hisaab.llm

import android.content.Context
import java.io.File

/**
 * Resolves on-device model availability for the Android on-device provider.
 *
 * Tier ladder (spec §8):
 *  1. Gemini Nano via AICore — detected by presence of the AICore client class.
 *     (Full Nano inference path is a follow-up; this slice only probes availability.
 *     play-services-aicore:16.0.0-alpha05 is not available in Google Maven so the
 *     compile-time dep is omitted; availability is detected via runtime class-probe.)
 *  2. Gemma 3 1B (int4) via MediaPipe — detected by the model .task file in app files.
 *  3. Neither -> unavailable (router falls through to cloud/template).
 */
class ModelManager(private val context: Context) {

    /** Gemma model file name (downloaded on-demand in M3-5; pre-placed for now). */
    val gemmaModelFile: File
        get() = File(context.filesDir, GEMMA_FILE)

    fun isAiCorePresent(): Boolean = runCatching {
        Class.forName("com.google.android.gms.ai.aicore.GenerativeModel")
        true
    }.getOrDefault(false)

    fun isGemmaPresent(): Boolean = gemmaModelFile.exists() && gemmaModelFile.length() > 0L

    /** Any on-device path usable right now. */
    fun isAnyModelAvailable(): Boolean = isGemmaPresent() || isAiCorePresent()

    companion object {
        const val GEMMA_FILE = "gemma-3-1b-it-int4.task"
    }
}
