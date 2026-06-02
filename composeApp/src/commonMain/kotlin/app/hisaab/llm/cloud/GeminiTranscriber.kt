package app.hisaab.llm.cloud

import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import app.hisaab.llm.LlmJson
import app.hisaab.llm.llmConfigured
import app.hisaab.llm.mapHttpError
import app.hisaab.platform.CloudTranscriber
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Cloud speech-to-text via Gemini generateContent with an inline audio part. This exists because the
 * on-device recognizers (Apple Speech / Android SpeechRecognizer) don't reliably support Bangla on
 * most phones — Gemini transcribes Bangla (and code-mixed "Banglish") far better. The transcript is
 * then handed to the SAME [app.hisaab.agent.AgentRuntime] parse step as typed/on-device input; this
 * only replaces the *listen* step, not the parse/save logic.
 *
 * The clip is sent as a base64 `inline_data` part (audio/wav). The API key is read at call time and
 * passed only in the x-goog-api-key header — never logged or stored in a field. Audio is held in
 * memory for the single request and not persisted.
 *
 * MODEL: gemini-2.5-flash (v1beta generateContent) — same model family the parse providers use.
 */
@OptIn(ExperimentalEncodingApi::class)
class GeminiTranscriber(
    httpClient: HttpClient,
    private val apiKey: () -> String?,
    private val model: String = "gemini-2.5-flash",
) : CloudTranscriber {

    private val client = httpClient.llmConfigured()
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    override suspend fun isAvailable(): Boolean = !apiKey().isNullOrBlank()

    override suspend fun transcribe(wav: ByteArray, languageHint: String): String {
        val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
        if (wav.isEmpty()) return ""

        val audioBase64 = Base64.encode(wav)
        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", transcriptionPrompt(languageHint)) }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "audio/wav")
                                put("data", audioBase64)
                            }
                        }
                    }
                }
            }
            // Deterministic, verbatim transcription — no creativity, plain text out.
            putJsonObject("generationConfig") {
                put("temperature", 0)
                put("responseMimeType", "text/plain")
            }
        }

        val response = client.post(endpoint) {
            header("x-goog-api-key", key)
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
        }
        if (!response.status.isSuccess()) {
            throw LlmException(mapHttpError(response.status, response.bodyAsText()))
        }
        return extractText(response.bodyAsText()).orEmpty().trim()
    }

    /**
     * Bias the model toward verbatim Bangla/Banglish for a Bangladeshi finance context. [languageHint]
     * ("bn" / "en") nudges decoding without forcing translation — the transcript keeps the spoken
     * language so the downstream parser sees what the user actually said.
     */
    private fun transcriptionPrompt(languageHint: String): String {
        // Empty / "auto" hint → let the model detect the spoken language (the Assistant mic uses this).
        val likely = when (languageHint.substringBefore('-').lowercase()) {
            "bn" -> "Bengali (Bangla), often mixed with English words and digits (\"Banglish\")"
            "en" -> "English, possibly mixed with Bengali words"
            else -> "either Bengali (Bangla), English, or a mix (\"Banglish\") — detect the spoken language from the audio"
        }
        return buildString {
            append("You are a speech transcription engine for a personal finance app used in Bangladesh. ")
            append("Transcribe the audio VERBATIM in the language actually spoken — do NOT translate. ")
            append("The speaker is most likely using $likely. ")
            append("Typical content: logging expenses and income in taka, e.g. \"cha 20 taka\", ")
            append("\"bazar e 500 kharoch korlam\", \"salary 45000 dhukse\". ")
            append("Output ONLY the transcript text — no quotes, no commentary, no JSON. ")
            append("If the audio is silent or unintelligible, output an empty string.")
        }
    }

    // Guarded: a malformed/blocked body (no parts, safety finishReason, corrupted JSON) degrades to
    // null → empty transcript → "Didn't catch that", never an uncaught parse throw.
    private fun extractText(body: String): String? = runCatching {
        LlmJson.json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
    }.getOrNull()
}
