package app.hisaab.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
