package app.hisaab.llm.cloud

import app.hisaab.agent.AgentProvider
import app.hisaab.agent.ChatMessage
import app.hisaab.agent.Role
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
 * Claude Messages API adapter. Forces structured output via a single tool with an
 * input_schema; reads the tool_use block's `input` object as the LLM JSON.
 *
 * API key is never logged or stored in a field; only read via the lambda at call time.
 * Redactor runs before the network call when [redact] returns true (evaluated at call time).
 *
 * MODEL: claude-3-5-haiku-latest (Messages API anthropic-version: 2023-06-01).
 */
class ClaudeProvider(
    httpClient: HttpClient,
    private val apiKey: () -> String?,
    private val model: String = "claude-3-5-haiku-latest",
    private val redact: suspend () -> Boolean = { true },
) : LlmProvider, AgentProvider {

    private val client = httpClient.llmConfigured()
    private val endpoint = "https://api.anthropic.com/v1/messages"
    private val anthropicVersion = "2023-06-01"

    override val id = ProviderId.CLOUD_CLAUDE

    override suspend fun isAvailable(): Boolean = !apiKey().isNullOrBlank()

    override suspend fun parse(req: ParseRequest): LlmParseResult {
        val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
        val text = if (redact()) Redactor.redact(req.text) else req.text
        val system = Prompts.extractionSystem(req.categories)
        val userMsg = buildString {
            req.senderHint?.let { appendLine("Sender: $it") }
            append("SMS: \"$text\"")
        }

        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 512)
            put("system", system)
            put("tool_choice", buildJsonObject {
                put("type", "tool")
                put("name", "record_transaction")
            })
            putJsonArray("tools") {
                addJsonObject {
                    put("name", "record_transaction")
                    put("description", "Record the extracted transaction fields from the SMS.")
                    put("input_schema", parseInputSchema(req.categories))
                }
            }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", userMsg)
                }
            }
        }

        val response = client.post(endpoint) {
            header("x-api-key", key)
            header("anthropic-version", anthropicVersion)
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload))
        }
        if (!response.status.isSuccess()) {
            throw LlmException(mapHttpError(response.status, response.bodyAsText()))
        }
        val bodyText = response.bodyAsText()
        val root = LlmJson.json.parseToJsonElement(bodyText).jsonObject
        val toolInput = root["content"]?.jsonArray
            ?.firstOrNull { it.jsonObject["type"]?.toString()?.contains("tool_use") == true }
            ?.jsonObject?.get("input")
            ?: throw LlmException(LlmError.Decode("no tool_use block"))
        return LlmJson.decodeResult(toolInput.toString())
    }

    override suspend fun categorize(merchant: String, categories: List<Category>): String? {
        val key = apiKey() ?: return null
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 64)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", Prompts.categorize(merchant, categories))
                }
            }
        }
        val response = client.post(endpoint) {
            header("x-api-key", key)
            header("anthropic-version", anthropicVersion)
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload))
        }
        if (!response.status.isSuccess()) return null
        val text = LlmJson.json.parseToJsonElement(response.bodyAsText()).jsonObject["content"]
            ?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return null
        return LlmJson.decodeCategoryId(text, categories.map { it.id }.toSet())
    }

    /**
     * Agent chat path (M4). Unlike [parse] there is no forced record_transaction tool — the agent
     * prompt instructs a raw JSON envelope, and we return Claude's text blocks verbatim for the
     * loop to decode. SYSTEM turns go in the top-level `system` field; USER/TOOL → "user",
     * ASSISTANT → "assistant". Redactor is intentionally NOT applied to agent text (master spec §10).
     */
    override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int): String {
        val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
        val system = messages.filter { it.role == Role.SYSTEM }.joinToString("\n") { it.content }
        val turns = messages.filter { it.role != Role.SYSTEM }
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") {
                turns.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == Role.ASSISTANT) "assistant" else "user")
                        put("content", m.content)
                    }
                }
            }
        }
        val response = client.post(endpoint) {
            header("x-api-key", key)
            header("anthropic-version", anthropicVersion)
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload))
        }
        if (!response.status.isSuccess()) {
            throw LlmException(mapHttpError(response.status, response.bodyAsText()))
        }
        return extractText(response.bodyAsText()) ?: throw LlmException(LlmError.Decode("no text content"))
    }

    /** Concatenate all `text` blocks from the Messages API `content` array; null if none. */
    private fun extractText(body: String): String? {
        val blocks = LlmJson.json.parseToJsonElement(body).jsonObject["content"]?.jsonArray ?: return null
        val text = blocks.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }.joinToString("")
        return text.ifBlank { null }
    }

    private fun parseInputSchema(categories: List<Category>) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("amount") { put("type", "number") }
            putJsonObject("direction") {
                put("type", "string"); putJsonArray("enum") { add("DEBIT"); add("CREDIT") }
            }
            putJsonObject("merchant") { put("type", "string") }
            putJsonObject("categoryId") {
                put("type", "string"); putJsonArray("enum") { categories.forEach { add(it.id) } }
            }
            putJsonObject("balanceAfter") { put("type", "number") }
            putJsonObject("refNo") { put("type", "string") }
            putJsonObject("confidence") { put("type", "number") }
            putJsonObject("isFinancial") { put("type", "boolean") }
        }
        putJsonArray("required") { add("confidence"); add("isFinancial") }
    }
}
