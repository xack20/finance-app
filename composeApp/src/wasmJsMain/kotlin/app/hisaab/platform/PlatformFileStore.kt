package app.hisaab.platform

actual class PlatformFileStore {
    actual fun attachmentsDir(): String = "/attachments"
    actual fun writeBytes(relativePath: String, bytes: ByteArray) {
        throw NotImplementedError("Web is viewer-only — attachments not supported")
    }
    actual fun readBytes(relativePath: String): ByteArray? = null
    actual fun delete(relativePath: String) {}
}
