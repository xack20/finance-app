package app.hisaab.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The relay payload is the JSON form of [RawCapture] (sealed by RelayCaptureCrypto). This guards
 * the contract that it serializes/deserializes losslessly — runnable on the JVM (no libsodium);
 * the crypto round-trip itself is covered by RelayCaptureCryptoInstrumentedTest.
 */
class RawCaptureSerializationTest {

    @Test
    fun `RawCapture round-trips through JSON`() {
        val cap = RawCapture(
            sender = "bKash",
            body = "You have received Tk 500.00 from 017XXXXXXXX. TrxID ABC123.",
            receivedAt = 1_717_300_000_000L,
            channel = CaptureChannel.SMS,
        )
        val json = Json.encodeToString(RawCapture.serializer(), cap)
        assertEquals(cap, Json.decodeFromString(RawCapture.serializer(), json))
    }

    @Test
    fun `every channel survives the round-trip`() {
        for (ch in CaptureChannel.entries) {
            val cap = RawCapture(sender = "s", body = "b", receivedAt = 1L, channel = ch)
            val json = Json.encodeToString(RawCapture.serializer(), cap)
            assertEquals(cap, Json.decodeFromString(RawCapture.serializer(), json))
        }
    }
}
