package app.hisaab.mac

import app.hisaab.capture.RelayCaptureCrypto
import app.hisaab.capture.SealedCapture
import app.hisaab.crypto.CryptoService
import app.hisaab.crypto.MnemonicService
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * End-to-end relay test ON the desktop JVM (real libsodium): the Mac seals via RelayPusher, then the
 * "phone side" opens the uploaded ciphertext with the same relay key → original capture. Also covers
 * cursor advancement and recovery-phrase → relay-key pairing.
 */
@OptIn(ExperimentalEncodingApi::class)
class RelayPusherTest {

    private val crypto = RelayCaptureCrypto()

    @Test
    fun pushOnce_seals_uploads_and_advances_cursor_and_phone_can_open() = runBlocking {
        val relayKey = crypto.relayKey(CryptoService().generateMasterSecret())
        val captures = listOf(
            RawCapture("bKash", "You have received Tk 500.00", 111L, CaptureChannel.SMS),
            RawCapture("City Bank", "debited BDT 200", 222L, CaptureChannel.SMS),
        )
        var savedCursor = -1L
        val uploaded = mutableListOf<RelayUpload>()
        val pusher = RelayPusher(
            relayCrypto = crypto,
            relayKey = relayKey,
            deviceId = "mac-test",
            readSince = { captures to 999L },
            uploader = { rows -> uploaded += rows },
            loadCursor = { 0L },
            saveCursor = { savedCursor = it },
        )

        val pushed = pusher.pushOnce()

        assertEquals(2, pushed)
        assertEquals(999L, savedCursor)
        assertEquals(2, uploaded.size)
        // Phone side: open each uploaded row with the same relay key → exact original capture.
        uploaded.forEachIndexed { i, up ->
            val sealed = SealedCapture(Base64.decode(up.nonceB64), Base64.decode(up.ciphertextB64))
            assertEquals(captures[i], crypto.open(sealed, relayKey))
            assertEquals("mac-test", up.deviceId)
        }
    }

    @Test
    fun empty_read_uploads_nothing_but_still_advances_cursor() = runBlocking {
        var savedCursor = -1L
        var uploadCalls = 0
        val pusher = RelayPusher(
            relayCrypto = crypto,
            relayKey = crypto.relayKey(CryptoService().generateMasterSecret()),
            deviceId = "mac-test",
            readSince = { emptyList<RawCapture>() to 500L },
            uploader = { uploadCalls++ },
            loadCursor = { 0L },
            saveCursor = { savedCursor = it },
        )
        assertEquals(0, pusher.pushOnce())
        assertEquals(500L, savedCursor)
        assertEquals(0, uploadCalls)
    }

    @Test
    fun recovery_phrase_derives_the_same_relay_key_as_the_phone() {
        val master = CryptoService().generateMasterSecret()
        val phrase = MnemonicService().encode(master).joinToString(" ")
        assertContentEquals(crypto.relayKey(master), Pairing.relayKeyFromPhrase(phrase))
    }

    @Test
    fun invalid_recovery_phrase_is_rejected() {
        assertFails { Pairing.relayKeyFromPhrase("not a valid bip39 recovery phrase at all here") }
    }
}
