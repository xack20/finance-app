package app.hisaab.design

import androidx.compose.ui.graphics.Color

object HisaabColors {

    /**
     * Full token surface for the "Midnight" design system.
     * Alpha tokens (hair/glass/xSoft) are translucent over [background].
     * `rule` and `gold` are legacy roles kept so existing screens compile until they migrate.
     */
    data class Palette(
        val background: Color,
        val backgroundInset: Color,
        val surface: Color,
        val surfaceRaised: Color,
        val hair: Color,
        val hair2: Color,
        val glass: Color,
        val onBackground: Color,
        val muted: Color,
        val faint: Color,
        val accent: Color,
        // NOTE: in Dark, accentDim is darker than accent (a subdued lime). In the derived Light
        // palette the relationship inverts — accentDim is the BRIGHTER hover/pressed fill, because
        // `accent` there is dimmed for AA text contrast. Pick the role, not the name, per theme.
        val accentDim: Color,
        val onAccent: Color,
        val accentSoft: Color,
        val positive: Color,
        val negative: Color,
        val positiveSoft: Color,
        val negativeSoft: Color,
        // legacy roles (existing screens reference these names)
        val rule: Color,
        val gold: Color,
    )

    /** Midnight — dark, primary. Exact from src/neo-theme.css. */
    val Dark = Palette(
        background     = Color(0xFF0A0B0E),
        backgroundInset = Color(0xFF101218),
        surface        = Color(0xFF161922),
        surfaceRaised  = Color(0xFF1D212B),
        hair           = Color(0x12FFFFFF), // white @ 7%
        hair2          = Color(0x0AFFFFFF), // white @ 4%
        glass          = Color(0x0CFFFFFF), // white @ 4.5%
        onBackground   = Color(0xFFF3F5F8),
        muted          = Color(0xFF98A0AD),
        faint          = Color(0xFF737B88),
        accent         = Color(0xFFCBF24A),
        accentDim      = Color(0xFFA9CE37),
        onAccent       = Color(0xFF0A0B0E),
        accentSoft     = Color(0x1FCBF24A), // lime @ 12%
        positive       = Color(0xFF46E08A),
        negative       = Color(0xFFFF6B5C),
        positiveSoft   = Color(0x1F46E08A),
        negativeSoft   = Color(0x1FFF6B5C),
        rule           = Color(0xFF242833), // solid approximation of hair over bg
        gold           = Color(0xFFA9CE37),
    )

    /**
     * Midnight — derived light variant. Lime is kept as a fill (with dark onAccent text);
     * `accent` here is dimmed so it passes AA when used as text/icon on a pale canvas.
     * Values are tuned to pass WCAG AA in ContrastTest (Task 2).
     */
    val Light = Palette(
        background     = Color(0xFFF6F8FB),
        backgroundInset = Color(0xFFEDF0F4),
        surface        = Color(0xFFFFFFFF),
        surfaceRaised  = Color(0xFFFFFFFF),
        hair           = Color(0x140A0B0E), // ink @ 8%
        hair2          = Color(0x0A0A0B0E),
        glass          = Color(0x0A0A0B0E), // intentionally same opacity as hair2 on the light canvas
        onBackground   = Color(0xFF13161B),
        muted          = Color(0xFF5A6573),
        faint          = Color(0xFF79828F), // darkened from #8A93A3 for WCAG AA (3.0) on light bg
        accent         = Color(0xFF4E6A10), // dimmed lime for AA text/icon use
        accentDim      = Color(0xFF5E7E12), // intentionally lighter than base accent on light (hover/pressed accent)
        onAccent       = Color(0xFF0A0B0E), // dark text on the bright lime fill
        accentSoft     = Color(0x1FCBF24A),
        positive       = Color(0xFF18854A),
        negative       = Color(0xFFC2392A),
        positiveSoft   = Color(0x1F18854A),
        negativeSoft   = Color(0x1FC2392A),
        rule           = Color(0xFFDDE2E8),
        gold           = Color(0xFF5E7E12),
    )

    /** The bright lime fill for CTAs/chips in the light theme (use with [Palette.onAccent]). */
    val LightAccentFill = Color(0xFFCBF24A)

    /** Vivid, theme-independent category hues (glyph chips + charts). */
    val categoryHues: Map<String, Color> = mapOf(
        "violet" to Color(0xFF8B7CFF),
        "blue"   to Color(0xFF4EA8FF),
        "teal"   to Color(0xFF2DD4BF),
        "amber"  to Color(0xFFFFB13C),
        "pink"   to Color(0xFFFF6FB5),
        "lime"   to Color(0xFFCBF24A),
        "rose"   to Color(0xFFFF6B5C),
        "slate"  to Color(0xFF8A93A3),
    )

}
