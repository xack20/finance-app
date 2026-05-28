package app.hisaab.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import com.ionspin.kotlin.crypto.util.LibsodiumRandom

/**
 * XChaCha20-Poly1305 file blob encryption for attachments.
 *
 * Used by AttachmentRepository (P0c-2): imageBytes → ciphertext on disk;
 * IV stored in the encrypted DB row. Both DB + disk file are needed to recover
 * the file. Key is the 32-byte db_key derived in CryptoService.
 */
@OptIn(ExperimentalUnsignedTypes::class)
class BlobCrypto {

    init {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initializeWithCallback { }
        }
    }

    /** Returns (ciphertext, iv). IV is 24 bytes for XChaCha20-Poly1305. */
    fun encrypt(plaintext: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
        require(key.size == 32) { "key must be 32 bytes" }
        val ivU = LibsodiumRandom.buf(24)
        val cipherU = AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfEncrypt(
            message = plaintext.toUByteArray(),
            associatedData = ubyteArrayOf(),
            nonce = ivU,
            key = key.toUByteArray(),
        )
        return cipherU.toByteArray() to ivU.toByteArray()
    }

    /** Throws if authentication tag check fails (wrong key, tampered ciphertext, wrong IV). */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(iv.size == 24) { "iv must be 24 bytes for XChaCha20-Poly1305" }
        val plainU = AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfDecrypt(
            ciphertextAndTag = ciphertext.toUByteArray(),
            associatedData = ubyteArrayOf(),
            nonce = iv.toUByteArray(),
            key = key.toUByteArray(),
        )
        return plainU.toByteArray()
    }
}
