package app.hisaab.llm

import android.content.Context
import app.hisaab.domain.Category
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * On-device LlmProvider backed by MediaPipe LLM Inference (Gemma 3 1B int4 .task).
 * isAvailable() is true only when a usable model file is present. parse() runs the
 * short prompt and decodes the JSON reply with the shared LlmJson validator.
 *
 * API key is never involved — fully local inference.
 * SMS body is never logged.
 *
 * Implements [Closeable] so the ~900MB LlmInference engine can be released on cleanup/test teardown.
 * Redactor runs before inference when [redact] returns true (evaluated at call time).
 *
 * SDK: tasks-genai 0.10.24.
 */
class AndroidOnDeviceProvider(
    private val context: Context,
    private val modelManager: ModelManager,
    private val redact: suspend () -> Boolean = { true },
) : LlmProvider, Closeable {

    override val id = ProviderId.ON_DEVICE

    @Volatile private var engine: LlmInference? = null

    override suspend fun isAvailable(): Boolean = modelManager.isGemmaPresent()

    private fun engineOrNull(): LlmInference? {
        engine?.let { return it }
        if (!modelManager.isGemmaPresent()) return null
        return synchronized(this) {
            engine ?: runCatching {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelManager.gemmaModelFile.absolutePath)
                    .setMaxTokens(1024)
                    .build()
                LlmInference.createFromOptions(context, options)
            }.getOrNull()?.also { engine = it }
        }
    }

    override suspend fun parse(req: ParseRequest): LlmParseResult = withContext(Dispatchers.Default) {
        val inference = engineOrNull() ?: throw LlmException(LlmError.Unavailable)
        val text = if (redact()) Redactor.redact(req.text) else req.text
        val prompt = buildString {
            append(Prompts.extractionSystemShort(req.categories))
            appendLine()
            req.senderHint?.let { appendLine("Sender: $it") }
            append("SMS: \"$text\"")
        }
        val raw = runCatching { inference.generateResponse(prompt) }
            .getOrElse { throw LlmException(LlmError.Decode(it.message ?: "inference failed")) }
        LlmJson.decodeResult(raw)
    }

    override suspend fun categorize(merchant: String, categories: List<Category>): String? =
        withContext(Dispatchers.Default) {
            val inference = engineOrNull() ?: return@withContext null
            val raw = runCatching { inference.generateResponse(Prompts.categorize(merchant, categories)) }
                .getOrNull() ?: return@withContext null
            LlmJson.decodeCategoryId(raw, categories.map { it.id }.toSet())
        }

    /** Releases the cached LlmInference engine. Safe to call multiple times. */
    override fun close() {
        synchronized(this) {
            engine?.close()
            engine = null
        }
    }
}
