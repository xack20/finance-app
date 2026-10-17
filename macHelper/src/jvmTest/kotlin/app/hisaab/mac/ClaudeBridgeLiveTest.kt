package app.hisaab.mac

import app.hisaab.bridge.AgentBridgeCrypto
import app.hisaab.bridge.BridgeRequest
import app.hisaab.bridge.BridgeResponse
import app.hisaab.bridge.BridgeTurn
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real end-to-end check of the Claude bridge: HTTP → BridgeServer → `claude -p` on this Mac's subscription.
 * Spends a tiny bit of subscription usage, so it only runs with HISAAB_BRIDGE_LIVE=1.
 */
class ClaudeBridgeLiveTest {

    @Test
    fun `a sealed request is answered by Claude Code on this Mac`() {
        if (System.getenv("HISAAB_BRIDGE_LIVE") != "1") return
        val crypto = AgentBridgeCrypto()
        val key = crypto.bridgeKey(Random.nextBytes(32))
        val cli = ClaudeCli()
        val server = BridgeServer(
            bridgeKey = { key },
            answer = { req -> cli.complete(req.system, req.turns).let { BridgeResponse(req.id, it.text, it.error, it.limitReached) } },
            port = 0,
        ).also { it.start() }
        try {
            val request = BridgeRequest(
                "live-1", System.currentTimeMillis(),
                system = """Reply with EXACTLY ONE JSON object: {"final":{"message":"<the sum>","proposedWrites":[]}}""",
                turns = listOf(BridgeTurn("user", "What is 19 + 23?"), BridgeTurn("assistant", "Let me think."), BridgeTurn("user", "Answer now.")),
            )
            val http = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/bridge"))
                .POST(HttpRequest.BodyPublishers.ofString(crypto.encodeEnvelope(crypto.sealRequest(request, key)))).build()
            val response = HttpClient.newHttpClient().send(http, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            val reply = crypto.openResponse(crypto.decodeEnvelope(response.body()), key)
            println("LIVE REPLY: ${reply.text} / error=${reply.error}")
            assertTrue(reply.text.orEmpty().contains("42"), reply.toString())
        } finally {
            server.stop()
        }
    }
}
