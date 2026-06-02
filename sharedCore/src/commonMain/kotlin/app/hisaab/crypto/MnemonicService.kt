package app.hisaab.crypto

import com.ionspin.kotlin.crypto.hash.Hash

class MnemonicService {

    @OptIn(ExperimentalUnsignedTypes::class)
    fun encode(entropy: ByteArray): List<String> {
        require(entropy.size == 32) { "Entropy must be 32 bytes" }
        // BIP39 standard: append first 8 bits of SHA256(entropy) as checksum.
        val checksumByte = Hash.sha256(entropy.toUByteArray()).toByteArray()[0]
        val bits = buildString {
            entropy.forEach { b -> append(b.toInt().and(0xFF).toString(2).padStart(8, '0')) }
            append(checksumByte.toInt().and(0xFF).toString(2).padStart(8, '0'))
        }
        return (0 until 24).map { i ->
            BIP39_WORDLIST[bits.substring(i * 11, i * 11 + 11).toInt(2)]
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun decode(words: List<String>): ByteArray {
        require(words.size == 24) { "Expected 24 words, got ${words.size}" }
        val bits = buildString {
            words.forEach { word ->
                val idx = BIP39_WORDLIST.indexOf(word)
                require(idx >= 0) { "Unknown BIP39 word: $word" }
                append(idx.toString(2).padStart(11, '0'))
            }
        }
        val entropy = ByteArray(32) { i -> bits.substring(i * 8, i * 8 + 8).toInt(2).toByte() }
        // Verify the 8-bit checksum
        val expectedChecksum = Hash.sha256(entropy.toUByteArray()).toByteArray()[0]
        val actualChecksum = bits.substring(256, 264).toInt(2).toByte()
        require(expectedChecksum == actualChecksum) { "Invalid BIP39 checksum" }
        return entropy
    }
}
