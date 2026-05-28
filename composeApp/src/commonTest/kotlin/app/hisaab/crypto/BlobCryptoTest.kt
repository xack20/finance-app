package app.hisaab.crypto

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Ignore("libsodium native lib unavailable in JVM unit tests — covered by P0c-3 Task 23 instrumented tests")
class BlobCryptoTest {

    private val crypto = BlobCrypto()
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun `encrypt then decrypt returns original bytes`() {
        val plaintext = "Hello receipt OCR".encodeToByteArray()
        val (ciphertext, iv) = crypto.encrypt(plaintext, key)
        assertTrue(plaintext.contentEquals(crypto.decrypt(ciphertext, key, iv)))
    }

    @Test
    fun `iv is 24 bytes for XChaCha20`() {
        val (_, iv) = crypto.encrypt(ByteArray(10), key)
        assertEquals(24, iv.size)
    }

    @Test
    fun `wrong key fails to decrypt`() {
        val (ciphertext, iv) = crypto.encrypt("secret".encodeToByteArray(), key)
        val wrongKey = ByteArray(32) { 0xFF.toByte() }
        try {
            crypto.decrypt(ciphertext, wrongKey, iv)
            assertTrue(false, "decrypt should have thrown with wrong key")
        } catch (_: Exception) { /* expected */ }
    }
}
