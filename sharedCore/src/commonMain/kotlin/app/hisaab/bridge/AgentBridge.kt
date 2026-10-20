package app.hisaab.bridge

import app.hisaab.crypto.BlobCrypto
import app.hisaab.crypto.CryptoService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** One turn of the conversation the phone's agent loop wants answered. [role] is "user" or "assistant". */
@Serializable
data class BridgeTurn(val role: String, val content: String)

/**
 * Phone → Mac: "answer this conversation with Claude". [id] + [sentAtMs] let the Mac refuse stale or replayed
 * requests; [ping] asks only whether the bridge and Claude Code are ready.
 */
@Serializable
data class BridgeRequest(
    val id: String,
    val sentAtMs: Long,
    val system: String = "",
    val turns: List<BridgeTurn> = emptyList(),
    val ping: Boolean = false,
)

/** Mac → phone: Claude's reply [text], or an [error]; [limitReached] = the subscription's usage limit was hit. */
@Serializable
data class BridgeResponse(
    val id: String,
    val text: String? = null,
    val error: String? = null,
    val limitReached: Boolean = false,
)

/** What travels over HTTP: base64 nonce + ciphertext. Plain HTTP is fine — only the paired devices can open it. */
@Serializable
data class BridgeEnvelope(val nonce: String, val ciphertext: String)

/**
 * E2EE for the phone ↔ Mac Claude bridge (spec 2026-10-17-agentic-hisaab-design, Phase 1b). Both devices already
 * derive the relay key from the recovery phrase; the bridge key is derived from it under its own label, so a
 * bridge message can never be mistaken for a relayed capture. XChaCha20-Poly1305 via [BlobCrypto].
 */
@OptIn(ExperimentalEncodingApi::class)
class AgentBridgeCrypto(
    private val crypto: CryptoService = CryptoService(),
    private val blob: BlobCrypto = BlobCrypto(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun bridgeKey(relayKey: ByteArray): ByteArray = crypto.hkdf(relayKey, BRIDGE_INFO, KEY_LEN)

    fun sealRequest(request: BridgeRequest, key: ByteArray) = seal(request, BridgeRequest.serializer(), key)
    fun openRequest(envelope: BridgeEnvelope, key: ByteArray) = open(envelope, BridgeRequest.serializer(), key)
    fun sealResponse(response: BridgeResponse, key: ByteArray) = seal(response, BridgeResponse.serializer(), key)
    fun openResponse(envelope: BridgeEnvelope, key: ByteArray) = open(envelope, BridgeResponse.serializer(), key)

    fun encodeEnvelope(envelope: BridgeEnvelope): String = json.encodeToString(BridgeEnvelope.serializer(), envelope)
    fun decodeEnvelope(text: String): BridgeEnvelope = json.decodeFromString(BridgeEnvelope.serializer(), text)

    private fun <T> seal(value: T, serializer: KSerializer<T>, key: ByteArray): BridgeEnvelope {
        val (ciphertext, nonce) = blob.encrypt(json.encodeToString(serializer, value).encodeToByteArray(), key)
        return BridgeEnvelope(Base64.encode(nonce), Base64.encode(ciphertext))
    }

    /** Throws if the envelope wasn't sealed with [key] or was altered. */
    private fun <T> open(envelope: BridgeEnvelope, serializer: KSerializer<T>, key: ByteArray): T {
        val plaintext = blob.decrypt(Base64.decode(envelope.ciphertext), key, Base64.decode(envelope.nonce))
        return json.decodeFromString(serializer, plaintext.decodeToString())
    }

    companion object {
        val BRIDGE_INFO: ByteArray = "hisaab/bridge/v1".encodeToByteArray()
        private const val KEY_LEN = 32
        /** The Mac companion listens here. */
        const val DEFAULT_PORT = 47800
    }
}
