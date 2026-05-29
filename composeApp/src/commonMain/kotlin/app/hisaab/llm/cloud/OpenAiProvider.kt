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
import io.ktor.client.request.header
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
 * OpenAI Chat Completions adapter using response_format json_schema (strict).
 *
 * API key passed as Authorization: Bearer header; never logged or stored in a field.
 * Redactor runs before the network call when [redact]=true (default).
 *
 * MODEL: gpt-4o-mini (Chat Completions response_format json_schema).
 */
class OpenAiProvider(
    httpClient: HttpClient,
    private val apiKey: () -> String?,
    private val model: String = "gpt-4o-mini",
    private val redact: Boolean = true,
) : LlmProvider {

    private val client = httpClient.llmConfigured()
    private val endpoint = "https://api.openai.com/v1/chat/completions"

    override val id = ProviderId.CLOUD_OPENAI

    override suspend fun isAvailable(): Boolean = !apiKey().isNullOrBlank()

    override suspend fun parse(req: ParseRequest): LlmParseResult {
        val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
        val text = if (redact) Redactor.redact(req.text) else req.text
        val userMsg = buildString {
            req.senderHint?.let { appendLine("Sender: $it") }
            append("SMS: \"$text\"")
        }

        val payload = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system"); put("content", Prompts.extractionSystem(req.categories))
                }
                addJsonObject { put("role", "user"); put("content", userMsg) }
            }
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "transaction_extraction")
                    put("strict", true)
                    put("schema", jsonSchema(req.categories))
                }
            }
        }

        val response = client.post(endpoint) {
            header("Authorization", "Bearer $key")
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
        }
        if (!response.status.isSuccess()) {
            throw LlmException(mapHttpError(response.status, response.bodyAsText()))
        }
        val content = extractContent(response.bodyAsText())
            ?: throw LlmException(LlmError.Decode("no message content"))
        return LlmJson.decodeResult(content)
    }

    override suspend fun categorize(merchant: String, categories: List<Category>): String? {
        val key = apiKey() ?: return null
        val payload = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject { put("role", "user"); put("content", Prompts.categorize(merchant, categories)) }
            }
            putJsonObject("response_format") { put("type", "json_object") }
        }
        val response = client.post(endpoint) {
            header("Authorization", "Bearer $key")
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
        }
        if (!response.status.isSuccess()) return null
        val content = extractContent(response.bodyAsText()) ?: return null
        return LlmJson.decodeCategoryId(content, categories.map { it.id }.toSet())
    }

    private fun extractContent(body: String): String? =
        LlmJson.json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content

    private fun jsonSchema(categories: List<Category>) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("amount") { putJsonArray("type") { add("number"); add("null") } }
            putJsonObject("direction") {
                putJsonArray("type") { add("string"); add("null") }
                putJsonArray("enum") { add("DEBIT"); add("CREDIT") }
            }
            putJsonObject("merchant") { putJsonArray("type") { add("string"); add("null") } }
            putJsonObject("categoryId") {
                putJsonArray("type") { add("string"); add("null") }
                putJsonArray("enum") { categories.forEach { add(it.id) } }
            }
            putJsonObject("balanceAfter") { putJsonArray("type") { add("number"); add("null") } }
            putJsonObject("refNo") { putJsonArray("type") { add("string"); add("null") } }
            putJsonObject("confidence") { put("type", "number") }
            putJsonObject("isFinancial") { put("type", "boolean") }
        }
        putJsonArray("required") {
            add("amount"); add("direction"); add("merchant"); add("categoryId")
            add("balanceAfter"); add("refNo"); add("confidence"); add("isFinancial")
        }
    }
}
