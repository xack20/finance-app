package app.hisaab.platform

import android.content.Context
import java.io.File

actual class PlatformFileStore(private val context: Context) {

    actual fun attachmentsDir(): String {
        val dir = File(context.filesDir, "attachments")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    actual fun writeBytes(relativePath: String, bytes: ByteArray) {
        val target = File(attachmentsDir(), relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    actual fun readBytes(relativePath: String): ByteArray? {
        val target = File(attachmentsDir(), relativePath)
        return if (target.exists()) target.readBytes() else null
    }

    actual fun delete(relativePath: String) {
        File(attachmentsDir(), relativePath).delete()
    }
}
