package app.hisaab.crypto

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Ignore("libsodium native lib unavailable in JVM unit tests — covered by P0c-3 Task 23 instrumented tests")
class MnemonicServiceTest {

    private val service = MnemonicService()

    @Test
    fun `encode 32 bytes produces 24 words`() {
        assertEquals(24, service.encode(ByteArray(32) { it.toByte() }).size)
    }

    @Test
    fun `all encoded words are in BIP39 wordlist`() {
        service.encode(ByteArray(32) { it.toByte() }).forEach { word ->
            assertTrue(word in BIP39_WORDLIST, "Word '$word' not in wordlist")
        }
    }

    @Test
    fun `encode then decode returns original bytes`() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        assertTrue(entropy.contentEquals(service.decode(service.encode(entropy))))
    }

    @Test
    fun `different entropy produces different words`() {
        val w1 = service.encode(ByteArray(32) { it.toByte() })
        val w2 = service.encode(ByteArray(32) { (it + 1).toByte() })
        assertTrue(w1 != w2)
    }

    @Test
    fun `BIP39 canonical vector — all zeros produces all abandon plus art`() {
        // BIP39 spec test vector: entropy 0x00…00 (256 bits) → "abandon" × 23 + "art"
        val entropy = ByteArray(32) { 0 }
        val words = service.encode(entropy)
        assertEquals(24, words.size)
        for (i in 0 until 23) assertEquals("abandon", words[i], "position $i should be 'abandon'")
        assertEquals("art", words[23])
    }

    @Test
    fun `decode validates checksum — flipping last word fails`() {
        val entropy = ByteArray(32) { 0 }
        val words = service.encode(entropy).toMutableList()
        words[23] = "zoo"
        try {
            service.decode(words)
            assertTrue(false, "decode should have thrown for invalid checksum")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }
}
