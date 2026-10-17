package app.hisaab.mac

import app.hisaab.bridge.AgentBridgeCrypto
import app.hisaab.bridge.BridgeRequest
import app.hisaab.bridge.BridgeResponse
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * The phone's "Claude via my Mac" endpoint: `POST /v1/bridge` with a sealed [BridgeRequest], answered with a
 * sealed [BridgeResponse]. Only a device holding the bridge key (derived from the same recovery phrase) can talk
 * to it; stale (> 5 min) and replayed requests are refused. Claude calls run one at a time.
 */
class BridgeServer(
    /** Null until the companion is paired — then every request is refused. */
    private val bridgeKey: () -> ByteArray?,
    private val answer: (BridgeRequest) -> BridgeResponse,
    private val ping: (BridgeRequest) -> BridgeResponse = { BridgeResponse(it.id, text = "ready") },
    private val now: () -> Long = System::currentTimeMillis,
    port: Int = AgentBridgeCrypto.DEFAULT_PORT,
    private val onServed: () -> Unit = {},
) {
    private val crypto = AgentBridgeCrypto()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)
    private val claudeLock = Any()

    // shortcut: replay memory is in-process (lost on restart); the 5-minute freshness window bounds the gap.
    private val seenIds = object : LinkedHashMap<String, Unit>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX_SEEN
    }

    /** The bound port (useful when started on port 0). */
    val port: Int get() = server.address.port

    fun start() {
        server.createContext("/v1/bridge") { exchange -> exchange.use { handle(it) } }
        // A Claude call can take minutes; pings and refusals must not wait behind it.
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
    }

    fun stop() {
        server.stop(0)
        (server.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") return exchange.reply(405, "")
        val key = bridgeKey() ?: return exchange.reply(503, "not paired")
        val request = runCatching {
            crypto.openRequest(crypto.decodeEnvelope(exchange.requestBody.readBytes().decodeToString()), key)
        }.getOrNull() ?: return exchange.reply(401, "")
        if (kotlin.math.abs(now() - request.sentAtMs) > MAX_AGE_MS) return exchange.reply(401, "stale")
        val firstTime = synchronized(seenIds) { seenIds.put(request.id, Unit) == null }
        if (!firstTime) return exchange.reply(409, "replayed")

        val response = if (request.ping) ping(request) else synchronized(claudeLock) { answer(request) }
        onServed()
        exchange.reply(200, crypto.encodeEnvelope(crypto.sealResponse(response, key)))
    }

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.encodeToByteArray()
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val MAX_AGE_MS = 5 * 60_000L
        const val MAX_SEEN = 2_000
    }
}
