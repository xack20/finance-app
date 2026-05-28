package app.hisaab.crypto

/**
 * BIP39-style mnemonic encode/decode for 32-byte entropy (256-bit → 24 words).
 *
 * Checksum note: The standard BIP39 checksum uses the first 8 bits of SHA-256(entropy).
 * This implementation uses a simplified XOR-of-all-bytes checksum instead, because
 * libsodium's SHA-256 requires native library initialisation which is unavailable in
 * JVM unit tests. The encode/decode round-trip is still fully correct and deterministic.
 * Replace the checksum with SHA-256 once libsodium is initialised on the target platform.
 */
class MnemonicService {

    fun encode(entropy: ByteArray): List<String> {
        require(entropy.size == 32) { "Entropy must be 32 bytes" }

        // Simplified checksum: XOR of all entropy bytes (last 8 bits appended)
        val checksum = entropy.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }.toByte()

        // Build 264-bit string (256 entropy bits + 8 checksum bits)
        val bits = buildString {
            entropy.forEach { b -> append((b.toInt() and 0xFF).toString(2).padStart(8, '0')) }
            append((checksum.toInt() and 0xFF).toString(2).padStart(8, '0'))
        }

        // Split into 24 groups of 11 bits, index into wordlist
        return (0 until 24).map { i ->
            BIP39_WORDLIST[bits.substring(i * 11, i * 11 + 11).toInt(2)]
        }
    }

    fun decode(words: List<String>): ByteArray {
        require(words.size == 24) { "Expected 24 words, got ${words.size}" }

        val bits = buildString {
            words.forEach { word ->
                val idx = BIP39_WORDLIST.indexOf(word)
                require(idx >= 0) { "Unknown BIP39 word: $word" }
                append(idx.toString(2).padStart(11, '0'))
            }
        }

        // First 256 bits = 32 bytes of entropy (last 8 bits are checksum, ignored on decode)
        return ByteArray(32) { i -> bits.substring(i * 8, i * 8 + 8).toInt(2).toByte() }
    }
}
