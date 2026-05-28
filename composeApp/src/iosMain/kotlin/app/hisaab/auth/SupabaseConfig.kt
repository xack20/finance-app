package app.hisaab.auth

import platform.Foundation.NSBundle

actual fun supabaseUrl(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("SUPABASE_URL") as? String ?: ""

actual fun supabaseAnonKey(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("SUPABASE_ANON_KEY") as? String ?: ""
