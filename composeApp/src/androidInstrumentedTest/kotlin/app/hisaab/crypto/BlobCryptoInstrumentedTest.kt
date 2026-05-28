package app.hisaab.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class BlobCryptoInstrumentedTest {

    private val crypto = BlobCrypto()
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun encrypt_then_decrypt_returns_original_bytes() {
        val plaintext = "Hello receipt OCR".encodeToByteArray()
        val (ciphertext, iv) = crypto.encrypt(plaintext, key)
        assertTrue(plaintext.contentEquals(crypto.decrypt(ciphertext, key, iv)))
    }

    @Test
    fun iv_is_24_bytes_for_XChaCha20() {
        val (_, iv) = crypto.encrypt(ByteArray(10), key)
        assertEquals(24, iv.size)
    }

    @Test
    fun wrong_key_fails_to_decrypt() {
        val (ciphertext, iv) = crypto.encrypt("secret".encodeToByteArray(), key)
        val wrongKey = ByteArray(32) { 0xFF.toByte() }
        try {
            crypto.decrypt(ciphertext, wrongKey, iv)
            fail("decrypt should have thrown with wrong key")
        } catch (_: Exception) { /* expected */ }
    }
}
