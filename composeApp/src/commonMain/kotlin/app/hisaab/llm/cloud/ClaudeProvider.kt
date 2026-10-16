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
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Claude Messages API adapter (raw HTTP so it stays in commonMain — there is no KMP Anthropic SDK).
 *
 * - parse: structured output (`output_config.format` json_schema) at effort `low`; the reply's text
 *   block is the transaction JSON. Forced `tool_choice` is NOT used — Opus 5.5 rejects it with a 400.
 * - complete (agent): effort `medium`, text blocks returned verbatim for the AgentLoop to decode.
 * - Every request opts into server-side refusal fallbacks (`fallbacks: "default"`); a refusal that
 *   survives the fallback chain surfaces as [LlmError.Refused], a truncated reply as [LlmError.Decode].
 *
 * API key is never logged or stored in a field; only read via the lambda at call time.
 * Redactor runs before the network call when [redact] returns true (evaluated at call time).
 */
class ClaudeProvider(
    httpClient: HttpClient,
    private val apiKey: () -> String?,
    private val model: String = "claude-opus-5-5",
    private val redact: suspend () -> Boolean = { true },
) : LlmProvider, AgentProvider {

    private val client = httpClient.llmConfigured()
    private val endpoint = "https://api.anthropic.com/v1/messages"
    private val anthropicVersion = "2023-06-01"
    private val fallbackBeta = "server-side-fallback-2026-07-01"

    override val id = ProviderId.CLOUD_CLAUDE

    override suspend fun isAvailable(): Boolean = !apiKey().isNullOrBlank()

    override suspend fun parse(req: ParseRequest): LlmParseResult {
        val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
        val text = if (redact()) Redactor.redact(req.text) else req.text
        val userMsg = buildString {
            req.senderHint?.let { appendLine("Sender: $it") }
            append("SMS: \"$text\"")
        }
        val payload = request(
            maxTokens = MIN_MAX_TOKENS,
            effort = "low",
            system = Prompts.extractionSystem(req.categories),
            turns = listOf("user" to userMsg),
        ) {
            putJsonObject("format") {
                put("type", "json_schema")
                put("schema", parseSchema(req.categories))
            }
        }
        return LlmJson.decodeResult(send(key, payload))
    }

    override suspend fun categorize(merchant: String, categories: List<Category>): String? {
        val key = apiKey() ?: return null
        val payload = request(
            maxTokens = MIN_MAX_TOKENS,
            effort = "low",
            system = null,
            turns = listOf("user" to Prompts.categorize(merchant, categories)),
        )
        val text = runCatching { send(key, payload) }.getOrNull() ?: return null
        return LlmJson.decodeCategoryId(text, categories.map { it.id }.toSet())
    }

    /**
     * Agent chat path (M4). The agent prompt instructs a raw JSON envelope, and we return Claude's text
     * blocks verbatim for the loop to decode. SYSTEM turns go in the top-level `system` field;
     * USER/TOOL → "user", ASSISTANT → "assistant". Redactor is intentionally NOT applied to agent text
     * (master spec §10).
     */
    override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int): String {
        val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
        val payload = request(
            maxTokens = maxOf(maxTokens, MIN_MAX_TOKENS),
            effort = "medium",
            system = messages.filter { it.role == Role.SYSTEM }.joinToString("\n") { it.content },
            turns = messages.filter { it.role != Role.SYSTEM }
                .map { (if (it.role == Role.ASSISTANT) "assistant" else "user") to it.content },
        )
        return send(key, payload) {
            // Thinking at medium effort can outlast the shared 15s cloud timeout on a non-streamed turn.
            timeout {
                requestTimeoutMillis = AGENT_TIMEOUT_MS
                socketTimeoutMillis = AGENT_TIMEOUT_MS
            }
        }
    }

    private fun request(
        maxTokens: Int,
        effort: String,
        system: String?,
        turns: List<Pair<String, String>>,
        outputConfig: JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("fallbacks", "default")
        if (!system.isNullOrBlank()) put("system", system)
        putJsonObject("output_config") {
            put("effort", effort)
            outputConfig()
        }
        putJsonArray("messages") {
            turns.forEach { (role, content) ->
                addJsonObject {
                    put("role", role)
                    put("content", content)
                }
            }
        }
    }

    /** POSTs [payload], checks status + stop_reason, and returns the final text. */
    private suspend fun send(
        key: String,
        payload: JsonObject,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): String {
        val response = client.post(endpoint) {
            header("x-api-key", key)
            header("anthropic-version", anthropicVersion)
            header("anthropic-beta", fallbackBeta)
            contentType(ContentType.Application.Json)
            setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
            configure()
        }
        if (!response.status.isSuccess()) {
            throw LlmException(mapHttpError(response.status, response.bodyAsText()))
        }
        val root = LlmJson.json.parseToJsonElement(response.bodyAsText()).jsonObject
        when (root["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> throw LlmException(
                LlmError.Refused(
                    root["stop_details"]?.jsonObject?.get("category")?.jsonPrimitive?.contentOrNull,
                ),
            )
            "max_tokens" -> throw LlmException(LlmError.Decode("reply truncated at max_tokens"))
        }
        return extractText(root) ?: throw LlmException(LlmError.Decode("no text content"))
    }

    /**
     * Concatenates the `text` blocks after the last `fallback` switch point (text before it belongs to
     * the declined attempt); thinking blocks are skipped. Null if there is no text.
     */
    private fun extractText(root: JsonObject): String? {
        val blocks = root["content"]?.jsonArray?.map { it.jsonObject } ?: return null
        val lastSwitch = blocks.indexOfLast { it.type() == "fallback" }
        val text = blocks.drop(lastSwitch + 1)
            .filter { it.type() == "text" }
            .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        return text.ifBlank { null }
    }

    private fun JsonObject.type(): String? = this["type"]?.jsonPrimitive?.contentOrNull

    private fun parseSchema(categories: List<Category>) = buildJsonObject {
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
        put("additionalProperties", false)
    }

    private companion object {
        /** Opus 5.5 always thinks and thinking counts toward max_tokens, so small caps truncate the reply. */
        const val MIN_MAX_TOKENS = 8192
        const val AGENT_TIMEOUT_MS = 60_000L
    }
}
