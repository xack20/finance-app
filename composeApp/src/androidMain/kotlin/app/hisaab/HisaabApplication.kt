package app.hisaab

import android.app.Application

class HisaabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        // Phase P0b: initialize SQLDelight, Supabase client, crash reporter
    }
}
