package app.hisaab

import android.app.Application

class HisaabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        app.hisaab.platform.CrashLog.install(this)
        System.loadLibrary("sqlcipher")
    }
}
