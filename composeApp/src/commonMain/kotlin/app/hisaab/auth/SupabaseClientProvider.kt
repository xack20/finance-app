package app.hisaab.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

// expect declarations live in SupabaseConfig.kt to avoid JVM class name collision
// with the platform-specific actual files.

val supabaseClient: SupabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = supabaseUrl(),
        supabaseKey = supabaseAnonKey(),
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }
}
