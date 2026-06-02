package app.hisaab.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.generichash.GenericHash
import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_OPSLIMIT_MODERATE
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_MEMLIMIT_MODERATE
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_argon2id_ALG_ARGON2ID13
import com.ionspin.kotlin.crypto.util.LibsodiumRandom

class CryptoService {

    init {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initializeWithCallback { }
        }
    }

    fun generateMasterSecret(): ByteArray =
        LibsodiumRandom.buf(32).toByteArray()

    fun deriveDbKey(masterSecret: ByteArray): ByteArray {
        require(masterSecret.size == 32) { "master_secret must be 32 bytes" }
        val salt = hkdf(masterSecret, "hisaab.db.salt".encodeToByteArray(), 16)
        // Hex-encode the random secret before passing to pwhash. The pwhash API
        // accepts a String, but masterSecret is uniformly-random bytes — UTF-8
        // decoding would silently replace invalid byte sequences with U+FFFD,
        // losing entropy. Hex encoding preserves all 256 bits deterministically
        // as a 64-character ASCII string.
        val passwordHex = masterSecret.joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
        return PasswordHash.pwhash(
            outputLength = 32,
            password = passwordHex,
            salt = salt.toUByteArray(),
            opsLimit = crypto_pwhash_OPSLIMIT_MODERATE,
            memLimit = crypto_pwhash_MEMLIMIT_MODERATE,
            algorithm = crypto_pwhash_argon2id_ALG_ARGON2ID13,
        ).toByteArray()
    }

    fun hkdf(ikm: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val state = GenericHash.genericHashInit(
            requestedHashLength = outputLength,
            key = ikm.toUByteArray(),
        )
        GenericHash.genericHashUpdate(state, info.toUByteArray())
        return GenericHash.genericHashFinal(state).toByteArray()
    }
}
