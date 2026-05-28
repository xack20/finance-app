package app.hisaab.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual class PlatformFileStore {

    actual fun attachmentsDir(): String {
        val docDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true,
        ).first() as String
        val path = "$docDir/attachments"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path, withIntermediateDirectories = true,
            attributes = null, error = null,
        )
        return path
    }

    actual fun writeBytes(relativePath: String, bytes: ByteArray) {
        // NSData <-> ByteArray bridging is non-trivial; defer to P0d.
        throw NotImplementedError("PlatformFileStore.writeBytes on iOS deferred to P0d")
    }

    actual fun readBytes(relativePath: String): ByteArray? = null

    actual fun delete(relativePath: String) {
        NSFileManager.defaultManager.removeItemAtPath(
            "${attachmentsDir()}/$relativePath", error = null,
        )
    }
}
