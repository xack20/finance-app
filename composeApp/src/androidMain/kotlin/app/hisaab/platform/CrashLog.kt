package app.hisaab.platform

import android.content.Context
import app.hisaab.BuildConfig
import java.io.File
import java.util.Date

/**
 * Keeps the last uncaught crash in app-private storage so a tester who installs from Drive (no adb) can
 * copy it from the app on the next launch. Stack traces only; the file never leaves the device by itself.
 */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText("${Date()} · ${BuildConfig.VERSION_NAME} · thread ${thread.name}\n${error.stackTraceToString()}")
            }
            previous?.uncaughtException(thread, error) // still let Android show its crash dialog and kill us
        }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
