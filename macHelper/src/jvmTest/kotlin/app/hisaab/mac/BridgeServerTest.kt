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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeServerTest {

    private val crypto = AgentBridgeCrypto()
    private val key = crypto.bridgeKey(Random(1).nextBytes(32))
    private val now = 1_000_000L
    private val asked = mutableListOf<BridgeRequest>()
    private val server = BridgeServer(
        bridgeKey = { key },
        answer = { req -> asked += req; BridgeResponse(req.id, text = "reply to ${req.turns.last().content}") },
        now = { now },
        port = 0,
    ).also { it.start() }

    @AfterTest fun stop() = server.stop()

    private fun post(request: BridgeRequest, withKey: ByteArray = key): HttpResponse<String> {
        val body = crypto.encodeEnvelope(crypto.sealRequest(request, withKey))
        val http = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/bridge"))
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        return HttpClient.newHttpClient().send(http, HttpResponse.BodyHandlers.ofString())
    }

    private fun reply(response: HttpResponse<String>) = crypto.openResponse(crypto.decodeEnvelope(response.body()), key)

    @Test
    fun `a sealed request gets a sealed reply from Claude`() {
        val response = post(BridgeRequest("r1", now, "sys", listOf(BridgeTurn("user", "hi"))))

        assertEquals(200, response.statusCode())
        assertEquals(BridgeResponse("r1", text = "reply to hi"), reply(response))
    }

    @Test
    fun `a request sealed with another key is refused without asking Claude`() {
        val response = post(BridgeRequest("r1", now, turns = listOf(BridgeTurn("user", "hi"))), withKey = Random(2).nextBytes(32))

        assertEquals(401, response.statusCode())
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a replayed or stale request is refused`() {
        val request = BridgeRequest("r1", now, turns = listOf(BridgeTurn("user", "hi")))
        assertEquals(200, post(request).statusCode())
        assertEquals(409, post(request).statusCode())
        assertEquals(401, post(BridgeRequest("r2", now - 6 * 60_000, turns = listOf(BridgeTurn("user", "hi")))).statusCode())
        assertEquals(1, asked.size)
    }

    @Test
    fun `a ping answers without asking Claude`() {
        val response = post(BridgeRequest("p1", now, ping = true))

        assertEquals(200, response.statusCode())
        assertEquals("p1", reply(response).id)
        assertTrue(asked.isEmpty())
    }
}
