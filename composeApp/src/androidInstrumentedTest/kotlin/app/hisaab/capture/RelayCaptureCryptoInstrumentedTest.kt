package app.hisaab.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hisaab.crypto.CryptoService
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

/**
 * Real-crypto coverage for the Mac→phone capture relay codec. Lives in androidInstrumentedTest
 * because libsodium's native lib isn't available in JVM unit tests (same reason BlobCryptoTest is
 * @Ignore'd). The JSON payload contract is covered separately by RawCaptureSerializationTest.
 */
@RunWith(AndroidJUnit4::class)
class RelayCaptureCryptoInstrumentedTest {

    private val crypto = CryptoService()
    private val relay = RelayCaptureCrypto(crypto = crypto)

    private val capture = RawCapture(
        sender = "bKash",
        body = "You have received Tk 500.00 from 017XXXXXXXX. TrxID ABC123.",
        receivedAt = 1_717_300_000_000L,
        channel = CaptureChannel.SMS,
    )

    @Test
    fun seal_then_open_round_trips() {
        val key = relay.relayKey(crypto.generateMasterSecret())
        val sealed = relay.seal(capture, key)
        assertEquals(24, sealed.nonce.size)
        assertEquals(capture, relay.open(sealed, key))
    }

    @Test
    fun nonces_are_random_but_both_open_to_the_same_capture() {
        val key = relay.relayKey(crypto.generateMasterSecret())
        val a = relay.seal(capture, key)
        val b = relay.seal(capture, key)
        assertFalse(a.nonce.contentEquals(b.nonce), "each seal must use a fresh random nonce")
        assertFalse(a.ciphertext.contentEquals(b.ciphertext), "ciphertext must differ per nonce")
        assertEquals(capture, relay.open(a, key))
        assertEquals(capture, relay.open(b, key))
    }

    @Test
    fun same_master_secret_derives_same_relay_key() {
        val master = crypto.generateMasterSecret()
        assertContentEquals(relay.relayKey(master), relay.relayKey(master))
    }

    @Test
    fun wrong_key_fails_to_open() {
        val keyA = relay.relayKey(crypto.generateMasterSecret())
        val keyB = relay.relayKey(crypto.generateMasterSecret())
        val sealed = relay.seal(capture, keyA)
        try {
            relay.open(sealed, keyB)
            fail("open should have thrown with the wrong relay key (AEAD tag mismatch)")
        } catch (_: Exception) { /* expected */ }
    }
}
