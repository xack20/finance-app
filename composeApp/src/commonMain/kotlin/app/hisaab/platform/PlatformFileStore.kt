package app.hisaab.platform

expect class PlatformFileStore {
    /** Returns absolute path to the per-app attachments directory. Creates it if missing. */
    fun attachmentsDir(): String
    fun writeBytes(relativePath: String, bytes: ByteArray)
    fun readBytes(relativePath: String): ByteArray?
    fun delete(relativePath: String)
}
