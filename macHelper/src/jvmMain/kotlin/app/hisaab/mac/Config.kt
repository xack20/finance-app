package app.hisaab.mac

import java.io.File
import java.util.Properties

/** Supabase project coordinates the helper connects to (same project as the phone app). */
data class CompanionConfig(val supabaseUrl: String, val supabaseAnonKey: String)

/**
 * Loads [CompanionConfig] without baking secrets into the repo: env vars first
 * (HISAAB_SUPABASE_URL / HISAAB_SUPABASE_ANON_KEY), else `~/.hisaab/companion.properties`
 * (keys `supabase.url`, `supabase.anon_key`). The anon key is public-by-design (RLS protects data).
 */
object Config {

    fun load(): CompanionConfig? {
        parse(System.getenv("HISAAB_SUPABASE_URL"), System.getenv("HISAAB_SUPABASE_ANON_KEY"))?.let { return it }
        val file = File(System.getProperty("user.home"), ".hisaab/companion.properties")
        if (!file.exists()) return null
        val props = Properties().apply { file.inputStream().use { load(it) } }
        return parse(props.getProperty("supabase.url"), props.getProperty("supabase.anon_key"))
    }

    /** Pure: a config only when both values are present and non-blank. */
    fun parse(url: String?, key: String?): CompanionConfig? =
        if (!url.isNullOrBlank() && !key.isNullOrBlank()) CompanionConfig(url.trim(), key.trim()) else null
}
