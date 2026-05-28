package app.hisaab.domain

data class Attachment(
    val id: String,
    val txnId: String,
    val mimeType: String,
    val filePath: String,
    val encryptedIv: ByteArray,
    val sizeBytes: Long,
    val createdAt: Long,
)
