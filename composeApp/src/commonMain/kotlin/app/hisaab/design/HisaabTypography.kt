package app.hisaab.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.hisaab.resources.Res
import app.hisaab.resources.hanken_grotesk_bold
import app.hisaab.resources.hanken_grotesk_medium
import app.hisaab.resources.hanken_grotesk_regular
import app.hisaab.resources.hanken_grotesk_semibold
import app.hisaab.resources.hind_siliguri_regular
import app.hisaab.resources.hind_siliguri_semibold
import app.hisaab.resources.noto_sans_bengali_regular
import app.hisaab.resources.space_grotesk_bold
import app.hisaab.resources.space_grotesk_medium
import app.hisaab.resources.space_grotesk_regular
import app.hisaab.resources.space_grotesk_semibold
import app.hisaab.resources.space_mono_bold
import app.hisaab.resources.space_mono_regular
import org.jetbrains.compose.resources.Font

/**
 * Midnight type system. The bundled families are built inside composition (FontFamily from
 * Compose Resources must be created in a @Composable on iOS/wasm), exposed via [families].
 * The base TextStyles default to a platform family so they stay usable in tests / non-composable
 * code; HisaabTheme rebinds them to [families] for the running app.
 */
object HisaabTypography {

    data class Family(val familyDescription: String, val compose: FontFamily)

    /** Descriptive placeholders for tests / non-composable use; real ones come from [families]. */
    val display = Family("Space Grotesk", FontFamily.SansSerif)
    val ui      = Family("Hanken Grotesk", FontFamily.SansSerif)
    val mono    = Family("Space Mono", FontFamily.Monospace)

    data class Families(val display: FontFamily, val ui: FontFamily, val mono: FontFamily)

    /**
     * Bundled families, built in composition. NOTE: each family lists a Latin face and a Bengali
     * face (Hind Siliguri / Noto Sans Bengali) at the SAME FontWeight slot to cover the হিসাব
     * wordmark and ৳ glyphs. Same-weight entries can shadow each other on some Compose targets —
     * verify the wordmark + Latin headlines render in the intended faces (smoke-tested in Phase 1
     * Task 8). If Latin text renders in the Bengali face, move the Bengali faces to a dedicated
     * Bengali FontFamily / rely on platform glyph fallback instead.
     */
    @Composable
    fun families(): Families = Families(
        display = FontFamily(
            Font(Res.font.space_grotesk_regular, FontWeight.Normal),
            Font(Res.font.space_grotesk_medium, FontWeight.Medium),
            Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold),
            Font(Res.font.space_grotesk_bold, FontWeight.Bold),
            // Bengali faces listed AFTER the Latin ones so Latin glyphs keep Space Grotesk; the
            // হিসাব wordmark renders at Bold (Splash/Lock/Welcome) and SemiBold, so register the
            // Bengali face at BOTH slots — otherwise Bold has no Bengali face and Compose measures
            // with a wide fallback while rendering narrow (the wordmark wrapped / clipped its last glyph).
            Font(Res.font.hind_siliguri_semibold, FontWeight.SemiBold),
            Font(Res.font.hind_siliguri_semibold, FontWeight.Bold),
        ),
        ui = FontFamily(
            Font(Res.font.hanken_grotesk_regular, FontWeight.Normal),
            Font(Res.font.hanken_grotesk_medium, FontWeight.Medium),
            Font(Res.font.hanken_grotesk_semibold, FontWeight.SemiBold),
            Font(Res.font.hanken_grotesk_bold, FontWeight.Bold),
            Font(Res.font.hind_siliguri_regular, FontWeight.Normal),
        ),
        mono = FontFamily(
            Font(Res.font.space_mono_regular, FontWeight.Normal),
            Font(Res.font.space_mono_bold, FontWeight.Bold),
            Font(Res.font.noto_sans_bengali_regular, FontWeight.Normal),
        ),
    )

    /**
     * Dedicated family for the হিসাব wordmark. Compose's FontFamily matcher selects ONE face per
     * weight and, when that face lacks the glyph, falls back to the SYSTEM font (wide metrics) rather
     * than a sibling face in the same family — so a mixed Latin+Bengali family measures the wordmark
     * with one font and renders it with another, and the last glyph wraps/clips. Giving the wordmark
     * a Hind-Siliguri-primary family makes measure == render. Use at SemiBold (the bundled weight).
     */
    @Composable
    fun wordmarkFamily(): FontFamily = FontFamily(
        // Noto Sans Bengali is the complete, bundled Bengali face — the bundled Hind Siliguri subset
        // is missing the ব glyph, which silently dropped the last letter of হিসাব when used alone.
        Font(Res.font.noto_sans_bengali_regular, FontWeight.Normal),
    )

    val heroAmount = TextStyle(
        fontFamily = display.compose, fontWeight = FontWeight.SemiBold,
        fontSize = 50.sp, letterSpacing = (-1.5).sp, lineHeight = 52.sp,
    )
    val title = TextStyle(
        fontFamily = display.compose, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, letterSpacing = (-0.6).sp, lineHeight = 34.sp,
    )
    val eyebrow = TextStyle(
        fontFamily = ui.compose, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, letterSpacing = 1.54.sp, lineHeight = 14.sp, // .14em × 11 (neo-theme.css:57)
    )
    val body = TextStyle(
        fontFamily = ui.compose, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    )
    /** 16px Hanken body — list merchant names, banner titles (design body/UI face, not Roboto). */
    val bodyLarge = TextStyle(
        fontFamily = ui.compose, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp,
    )
    /** 18px/700 Hanken — card titles ("Log transactions automatically"). */
    val titleMedium = TextStyle(
        fontFamily = ui.compose, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
    )
    val label = TextStyle(
        fontFamily = ui.compose, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 1.54.sp, lineHeight = 14.sp, // .14em × 11
    )
    val tabular = TextStyle(
        fontFamily = mono.compose, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum, lnum",
    )
}
