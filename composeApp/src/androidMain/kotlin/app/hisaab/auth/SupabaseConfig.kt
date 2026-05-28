package app.hisaab.auth

import app.hisaab.BuildConfig

actual fun supabaseUrl(): String = BuildConfig.SUPABASE_URL
actual fun supabaseAnonKey(): String = BuildConfig.SUPABASE_ANON_KEY
