package app.hisaab.mac

import java.io.File

/**
 * Persists the chat.db read cursor (an Apple-epoch nanosecond `Long`, stored as plain text) so the
 * helper resumes where it left off across launches. Default location:
 * `~/Library/Application Support/HisaabCompanion/cursor`. Not sensitive (just a timestamp).
 */
class FileCursorStore(private val file: File = defaultFile()) {

    fun load(): Long = runCatching { file.readText().trim().toLong() }.getOrDefault(0L)

    fun save(value: Long) {
        file.parentFile?.mkdirs()
        file.writeText(value.toString())
    }

    companion object {
        fun defaultFile(): File =
            File(System.getProperty("user.home"), "Library/Application Support/HisaabCompanion/cursor")
    }
}
