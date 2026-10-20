package app.hisaab.llm.cloud

import app.hisaab.agent.AgentProvider
import app.hisaab.agent.ChatMessage
import app.hisaab.agent.Role
import app.hisaab.bridge.AgentBridgeCrypto
import app.hisaab.bridge.BridgeRequest
import app.hisaab.bridge.BridgeResponse
import app.hisaab.bridge.BridgeTurn
import app.hisaab.domain.Category
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import app.hisaab.llm.LlmParseResult
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlin.random.Random

/** Seals a request for the Mac and opens its reply (libsodium in production; plain JSON in tests). */
interface BridgeCodec {
    fun seal(request: BridgeRequest): String
    fun open(body: String): BridgeResponse
}

/** [BridgeCodec] under the bridge key derived from the relay key both devices get from the recovery phrase. */
class CryptoBridgeCodec(private val bridgeKey: ByteArray) : BridgeCodec {
    private val crypto = AgentBridgeCrypto()
    override fun seal(request: BridgeRequest) = crypto.encodeEnvelope(crypto.sealRequest(request, bridgeKey))
    override fun open(body: String) = crypto.openResponse(crypto.decodeEnvelope(body), bridgeKey)
}

/**
 * "Claude via my Mac": the agent's model calls go to the Hisaab Companion on the user's Mac, which answers them
 * with Claude Code on the user's Claude subscription — no API key (spec 2026-10-17, Phase 1b). Only
 * [AgentProvider.complete] is supported; the per-message [parse] path is unused since the capture agent.
 */
class MacBridgeProvider(
    httpClient: HttpClient,
    /** What the user typed: "192.168.1.5", or "host:port" (e.g. a Tailscale name). Null when not set up. */
    private val address: suspend () -> String?,
    /** Null until the phone holds its master secret (locked / not onboarded). */
    private val codec: suspend () -> BridgeCodec?,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : LlmProvider, AgentProvider {

    private val client = httpClient.config { install(HttpTimeout) }

    override val id = ProviderId.CLOUD_CLAUDE

    override suspend fun isAvailable(): Boolean = !address().isNullOrBlank() && codec() != null

    /** "Validate": is the Mac reachable, paired with this phone, and is Claude Code installed? */
    override suspend fun verifyKey() {
        val reply = send(BridgeRequest(newId(), now(), ping = true), PING_TIMEOUT_MS)
        reply.error?.let { throw LlmException(LlmError.ProviderError(HttpStatusCode.ServiceUnavailable.value, it)) }
    }

    override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int): String {
        val request = BridgeRequest(
            id = newId(),
            sentAtMs = now(),
            system = messages.filter { it.role == Role.SYSTEM }.joinToString("\n") { it.content },
            turns = messages.filter { it.role != Role.SYSTEM }
                .map { BridgeTurn(if (it.role == Role.ASSISTANT) "assistant" else "user", it.content) },
        )
        val reply = send(request, CALL_TIMEOUT_MS)
        if (reply.limitReached) throw LlmException(LlmError.RateLimited)
        reply.error?.let { throw LlmException(LlmError.ProviderError(HttpStatusCode.BadGateway.value, it)) }
        return reply.text ?: throw LlmException(LlmError.Decode("the Mac returned no reply"))
    }

    private suspend fun send(request: BridgeRequest, timeoutMs: Long): BridgeResponse {
        val host = address()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw LlmException(LlmError.InvalidKey) // no Mac address saved
        val codec = codec() ?: throw LlmException(LlmError.Network("unlock Hisaab to reach your Mac"))
        val url = "http://" + (if (':' in host) host else "$host:${AgentBridgeCrypto.DEFAULT_PORT}") + "/v1/bridge"
        val response = try {
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(codec.seal(request))
                timeout {
                    requestTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LlmException(LlmError.Network("your Mac isn't reachable at $host (${e.message})"))
        }
        when (response.status) {
            HttpStatusCode.OK -> Unit
            // The Mac couldn't open our request: it was paired with a different recovery phrase.
            HttpStatusCode.Unauthorized -> throw LlmException(LlmError.InvalidKey)
            else -> throw LlmException(LlmError.ProviderError(response.status.value, response.bodyAsText().take(200)))
        }
        val reply = runCatching { codec.open(response.bodyAsText()) }
            .getOrElse { throw LlmException(LlmError.Decode("couldn't open the Mac's reply")) }
        if (reply.id != request.id) throw LlmException(LlmError.Decode("the Mac answered a different request"))
        return reply
    }

    override suspend fun parse(req: ParseRequest): LlmParseResult =
        throw LlmException(LlmError.ProviderError(HttpStatusCode.NotImplemented.value, "not supported via the Mac"))

    override suspend fun categorize(merchant: String, categories: List<Category>): String? = null

    private fun newId(): String = Random.Default.nextBytes(12).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private companion object {
        /** Claude Code on a 25-message batch, plus the Mac's own 5-minute cap. */
        const val CALL_TIMEOUT_MS = 6 * 60_000L
        const val PING_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 8_000L
    }
}
