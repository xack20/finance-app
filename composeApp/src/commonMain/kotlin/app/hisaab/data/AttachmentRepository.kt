package app.hisaab.data

import app.hisaab.crypto.BlobCrypto
import app.hisaab.crypto.CryptoService
import app.hisaab.db.HisaabDatabase
import app.hisaab.platform.PlatformFileStore
import kotlinx.datetime.Clock
import kotlin.random.Random

class AttachmentRepository(
    private val db: HisaabDatabase,
    private val blobCrypto: BlobCrypto,
    private val masterSecretProvider: () -> ByteArray?,
    private val fileStore: PlatformFileStore,
    private val cryptoService: CryptoService,
) {

    suspend fun attach(txnId: String, imageBytes: ByteArray, mimeType: String): String {
        val secret = masterSecretProvider()
            ?: error("DB locked — cannot attach (master_secret not in memory)")
        val dbKey = cryptoService.deriveDbKey(secret)
        val (ciphertext, iv) = blobCrypto.encrypt(imageBytes, dbKey)
        dbKey.fill(0)

        val id = randomId()
        val relativePath = "$txnId/$id.bin"
        fileStore.writeBytes(relativePath, ciphertext)

        db.transactionQueriesQueries.insertAttachment(
            id = id,
            txn_id = txnId,
            mime_type = mimeType,
            file_path = relativePath,
            encrypted_iv = iv,
            size_bytes = imageBytes.size.toLong(),
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
        return id
    }

    suspend fun decrypt(attachmentId: String): ByteArray? {
        val record = db.transactionQueriesQueries.getAttachment(attachmentId).executeAsOneOrNull()
            ?: return null
        val ciphertext = fileStore.readBytes(record.file_path) ?: return null
        val secret = masterSecretProvider()
            ?: error("DB locked — cannot decrypt (master_secret not in memory)")
        val dbKey = cryptoService.deriveDbKey(secret)
        val plaintext = blobCrypto.decrypt(ciphertext, dbKey, record.encrypted_iv)
        dbKey.fill(0)
        return plaintext
    }

    suspend fun delete(attachmentId: String) {
        val record = db.transactionQueriesQueries.getAttachment(attachmentId).executeAsOneOrNull()
            ?: return
        fileStore.delete(record.file_path)
        db.transactionQueriesQueries.deleteAttachment(attachmentId)
    }

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
