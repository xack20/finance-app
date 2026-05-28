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
        return PasswordHash.pwhash(
            outputLength = 32,
            password = masterSecret.decodeToString(),
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
