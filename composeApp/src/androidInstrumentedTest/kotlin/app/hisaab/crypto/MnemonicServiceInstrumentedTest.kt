package app.hisaab.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class MnemonicServiceInstrumentedTest {

    private val service = MnemonicService()

    @Test
    fun encode_32_bytes_produces_24_words() {
        assertEquals(24, service.encode(ByteArray(32) { it.toByte() }).size)
    }

    @Test
    fun all_encoded_words_are_in_BIP39_wordlist() {
        service.encode(ByteArray(32) { it.toByte() }).forEach { word ->
            assertTrue(word in BIP39_WORDLIST, "Word '$word' not in wordlist")
        }
    }

    @Test
    fun encode_then_decode_returns_original_bytes() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        assertTrue(entropy.contentEquals(service.decode(service.encode(entropy))))
    }

    @Test
    fun BIP39_canonical_vector_all_zeros_produces_abandon_plus_art() {
        // BIP39 spec test vector: entropy 0x00...00 (256 bits) -> "abandon" x 23 + "art"
        val entropy = ByteArray(32) { 0 }
        val words = service.encode(entropy)
        assertEquals(24, words.size)
        for (i in 0 until 23) assertEquals("abandon", words[i], "position $i should be 'abandon'")
        assertEquals("art", words[23])
    }

    @Test
    fun decode_validates_checksum_flipping_last_word_fails() {
        val entropy = ByteArray(32) { 0 }
        val words = service.encode(entropy).toMutableList()
        words[23] = "zoo"
        try {
            service.decode(words)
            fail("decode should have thrown for invalid checksum")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }
}
