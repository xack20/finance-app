package app.hisaab.mac

import app.hisaab.bridge.AgentBridgeCrypto
import app.hisaab.bridge.BridgeRequest
import app.hisaab.bridge.BridgeResponse
import app.hisaab.bridge.BridgeTurn
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AgentBridgeCryptoTest {

    private val crypto = AgentBridgeCrypto()
    private val relayKey = Random(7).nextBytes(32)

    @Test
    fun `a request opens on the other side exactly as it was sent`() {
        val key = crypto.bridgeKey(relayKey)
        val request = BridgeRequest("r1", sentAtMs = 42, system = "sys", turns = listOf(BridgeTurn("user", "Tk 500 ৳")))

        assertEquals(request, crypto.openRequest(crypto.sealRequest(request, key), key))
    }

    @Test
    fun `a reply opens the same way`() {
        val key = crypto.bridgeKey(relayKey)
        val response = BridgeResponse("r1", text = """{"final":{}}""")

        assertEquals(response, crypto.openResponse(crypto.sealResponse(response, key), key))
    }

    @Test
    fun `the bridge key is not the relay key and the wrong key can't open`() {
        val key = crypto.bridgeKey(relayKey)
        assertNotEquals(relayKey.toList(), key.toList())
        val sealed = crypto.sealRequest(BridgeRequest("r1", 1), key)

        assertFailsWith<Exception> { crypto.openRequest(sealed, crypto.bridgeKey(Random(8).nextBytes(32))) }
    }

    @Test
    fun `a tampered message is rejected`() {
        val key = crypto.bridgeKey(relayKey)
        val sealed = crypto.sealRequest(BridgeRequest("r1", 1), key)
        val flipped = sealed.copy(ciphertext = sealed.ciphertext.reversed())

        assertFailsWith<Exception> { crypto.openRequest(flipped, key) }
    }
}
