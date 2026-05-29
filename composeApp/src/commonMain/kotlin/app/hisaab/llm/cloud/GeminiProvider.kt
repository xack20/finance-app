package app.hisaab.llm.cloud

import app.hisaab.domain.Category
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import app.hisaab.llm.LlmJson
import app.hisaab.llm.LlmParseResult
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId
import app.hisaab.llm.Prompts
import app.hisaab.llm.Redactor
import app.hisaab.llm.llmConfigured
import app.hisaab.llm.mapHttpError
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Gemini generateContent adapter. Uses generationConfig.responseSchema +
 * responseMimeType=application/json so the candidate text IS the structured JSON.
 *
 * API key is passed in the query string (?key=); never logged or stored in a field.
 * Redactor runs before the network call when [redact]=true (default).
 *
 * MODEL: gemini-2.0-flash (v1beta generateContent).
 */
class GeminiProvider(
    httpClient: HttpClient,
    private val apiKey: () -> String?,
    private val model: String = "gemini-2.0-flash",
    private val redact: Boolean = true,
) : LlmProvider {

    private val client = httpClient.llmConfigured()
    private fun endpoint(key: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

    override val id = ProviderId.CLOUD_GEMINI

    override suspend fun isAvailable(): Boolean = !apiKey().isNullOrBlank()

    override suspend fun parse(req: ParseRequest): LlmParseResult {
        val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
        val text = if (redact) Redactor.redact(req.text) else req.text
        val prompt = buildString {
            append(Prompts.extractionSystem(req.categories))
            appendLine()
            req.senderHint?.let { appendLine("Sender: $it") }
            append("SMS: \"$text\"")
        }

        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", responseSchema(req.categories))
            }
        }

        val response = client.post(endpoint(key)) {
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
        }
        if (!response.status.isSuccess()) {
            throw LlmException(mapHttpError(response.status, response.bodyAsText()))
        }
        val candidateText = extractText(response.bodyAsText())
            ?: throw LlmException(LlmError.Decode("no candidate text"))
        return LlmJson.decodeResult(candidateText)
    }

    override suspend fun categorize(merchant: String, categories: List<Category>): String? {
        val key = apiKey() ?: return null
        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") { addJsonObject { put("text", Prompts.categorize(merchant, categories)) } }
                }
            }
            putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
        }
        val response = client.post(endpoint(key)) {
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
        }
        if (!response.status.isSuccess()) return null
        val text = extractText(response.bodyAsText()) ?: return null
        return LlmJson.decodeCategoryId(text, categories.map { it.id }.toSet())
    }

    private fun extractText(body: String): String? =
        LlmJson.json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

    private fun responseSchema(categories: List<Category>) = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("amount") { put("type", "NUMBER") }
            putJsonObject("direction") {
                put("type", "STRING"); putJsonArray("enum") { add("DEBIT"); add("CREDIT") }
            }
            putJsonObject("merchant") { put("type", "STRING") }
            putJsonObject("categoryId") {
                put("type", "STRING"); putJsonArray("enum") { categories.forEach { add(it.id) } }
            }
            putJsonObject("balanceAfter") { put("type", "NUMBER") }
            putJsonObject("refNo") { put("type", "STRING") }
            putJsonObject("confidence") { put("type", "NUMBER") }
            putJsonObject("isFinancial") { put("type", "BOOLEAN") }
        }
        putJsonArray("required") { add("confidence"); add("isFinancial") }
    }
}
