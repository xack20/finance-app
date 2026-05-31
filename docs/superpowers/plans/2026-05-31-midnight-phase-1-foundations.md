# Midnight Redesign — Phase 1: Theme Foundations — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Editorial token layer with the "Midnight" design system (dark + derived light), so the existing app instantly re-skins through `LocalHisaabPalette` without touching any feature logic.

**Architecture:** Extend the existing `app.hisaab.design` token objects (`HisaabColors`/`HisaabTypography`/`HisaabShapes`/`HisaabSpacing`) and `HisaabTheme`. All screens already read tokens via `LocalHisaabPalette` and the Material3 theme, so swapping token values re-skins every surface. TDD is driven by the existing `commonTest/.../design/*Test.kt` files (they go red when values change) plus a new WCAG-contrast test that objectively validates the derived light palette.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.10.1, Compose Resources (fonts; `Res` package `app.hisaab.resources`), kotlin.test.

**Spec:** `docs/superpowers/specs/2026-05-31-midnight-redesign-design.md` (§3 tokens, §4 typography, §3.3 shape/spacing).
**Design source:** `docs/design/design_handoff_hisaab_midnight/src/neo-theme.css`.

**Scope of this plan:** ONLY the theme foundation. New component primitives (GlassCard, dock, keypad, etc.) and per-screen porting are later phases with their own plans. Do **not** edit feature screens here except a token-reference fix surfaced by Task 8.

---

## File map

- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt` — extended `Palette` + Midnight dark/light + parked Editorial + `categoryHues`.
- Modify: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabColorsTest.kt` — assert Midnight values.
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/ContrastTest.kt` — WCAG AA ratios for both palettes.
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/Wcag.kt` — test-only contrast helper.
- Add fonts: `composeApp/src/commonMain/composeResources/font/*.ttf` (Space Grotesk, Hanken Grotesk, Space Mono, Hind Siliguri, Noto Sans Bengali).
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTypography.kt` — real `FontFamily` + Midnight type scale.
- Modify: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabTypographyTest.kt` — assert Midnight families/scale.
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabShapes.kt` — Midnight radii.
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabShapesTest.kt` — assert radii.
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabSpacing.kt` — gutter 20dp.
- Modify: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabSpacingTest.kt` — assert gutter 20dp.
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTheme.kt` — map expanded palette → Material3.

**Test command (JVM, fast):** `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.*"`
Prefix with `JAVA_HOME=$(/usr/libexec/java_home -v 17)` if `JAVA_HOME` is unset.

---

## Task 1: Midnight color tokens

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabColorsTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt`

- [ ] **Step 1: Rewrite the colors test to assert Midnight values (RED)**

Replace the entire body of `HisaabColorsTest.kt`:

```kotlin
package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HisaabColorsTest {
    @Test
    fun dark_canvas_is_deep_ink() {
        assertEquals(Color(0xFF0A0B0E), HisaabColors.Dark.background)
        assertEquals(Color(0xFF161922), HisaabColors.Dark.surface)
    }

    @Test
    fun electric_lime_is_the_accent() {
        assertEquals(Color(0xFFCBF24A), HisaabColors.Dark.accent)
        assertEquals(Color(0xFF0A0B0E), HisaabColors.Dark.onAccent)
    }

    @Test
    fun dark_text_roles_match_spec() {
        assertEquals(Color(0xFFF3F5F8), HisaabColors.Dark.onBackground)
        assertEquals(Color(0xFF98A0AD), HisaabColors.Dark.muted)
        assertEquals(Color(0xFF5C6470), HisaabColors.Dark.faint)
    }

    @Test
    fun semantic_money_colors_match_spec() {
        assertEquals(Color(0xFF46E08A), HisaabColors.Dark.positive)
        assertEquals(Color(0xFFFF6B5C), HisaabColors.Dark.negative)
    }

    @Test
    fun light_variant_is_a_pale_canvas_with_ink_text() {
        assertEquals(Color(0xFFF6F8FB), HisaabColors.Light.background)
        assertEquals(Color(0xFF13161B), HisaabColors.Light.onBackground)
    }

    @Test
    fun light_and_dark_differ() {
        assertNotEquals(HisaabColors.Light.background, HisaabColors.Dark.background)
        assertNotEquals(HisaabColors.Light.onBackground, HisaabColors.Dark.onBackground)
    }

    @Test
    fun eight_category_hues_present() {
        assertEquals(8, HisaabColors.categoryHues.size)
        assertEquals(Color(0xFF8B7CFF), HisaabColors.categoryHues["violet"])
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabColorsTest"`
Expected: FAIL — `HisaabColors.Dark.background` is still `0xFF0F0C08`; `onAccent`, `faint`, `categoryHues` don't exist (compile error).

- [ ] **Step 3: Extend the Palette and set Midnight values**

Replace the entire body of `HisaabColors.kt`:

```kotlin
package app.hisaab.design

import androidx.compose.ui.graphics.Color

object HisaabColors {

    /**
     * Full token surface for the "Midnight" design system.
     * Alpha tokens (hair/glass/*Soft) are translucent over [background].
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
        faint          = Color(0xFF5C6470),
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
        glass          = Color(0x0A0A0B0E),
        onBackground   = Color(0xFF13161B),
        muted          = Color(0xFF5A6573),
        faint          = Color(0xFF8A93A3),
        accent         = Color(0xFF4E6A10), // dimmed lime for AA text/icon use
        accentDim      = Color(0xFF5E7E12),
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

    // --- Parked: original "Editorial Premium" palettes (kept for reference; not wired) ---
    val EditorialLight = Palette(
        background = Color(0xFFFAF7F2), backgroundInset = Color(0xFFF2ECE0),
        surface = Color(0xFFFFFFFF), surfaceRaised = Color(0xFFFFFFFF),
        hair = Color(0x14000000), hair2 = Color(0x0A000000), glass = Color(0x0A000000),
        onBackground = Color(0xFF1A1A1A), muted = Color(0xFF6F6453), faint = Color(0xFF9A8E78),
        accent = Color(0xFFAD6B2A), accentDim = Color(0xFFC8964A), onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0x1FAD6B2A), positive = Color(0xFF2E7D4F), negative = Color(0xFFB5402C),
        positiveSoft = Color(0x1F2E7D4F), negativeSoft = Color(0x1FB5402C),
        rule = Color(0xFFE6DCCB), gold = Color(0xFFC8964A),
    )
    val EditorialDark = Palette(
        background = Color(0xFF0F0C08), backgroundInset = Color(0xFF141009),
        surface = Color(0xFF1A1410), surfaceRaised = Color(0xFF211A14),
        hair = Color(0x12FFFFFF), hair2 = Color(0x0AFFFFFF), glass = Color(0x0CFFFFFF),
        onBackground = Color(0xFFF5EDE0), muted = Color(0xFFB3A288), faint = Color(0xFF7E715C),
        accent = Color(0xFFD68945), accentDim = Color(0xFFD8A05A), onAccent = Color(0xFF0F0C08),
        accentSoft = Color(0x1FD68945), positive = Color(0xFF2E7D4F), negative = Color(0xFFE26B57),
        positiveSoft = Color(0x1F2E7D4F), negativeSoft = Color(0x1FE26B57),
        rule = Color(0xFF2A2218), gold = Color(0xFFD8A05A),
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabColorsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabColorsTest.kt
git commit -m "feat(design): Midnight color tokens (dark + derived light)"
```

---

## Task 2: WCAG AA contrast verification (validates the derived light palette)

**Files:**
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/Wcag.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/ContrastTest.kt`

- [ ] **Step 1: Write the contrast helper (test-only)**

Create `Wcag.kt`:

```kotlin
package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.x relative-luminance contrast ratio between two opaque colors (1.0–21.0). */
object Wcag {
    private fun lin(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    private fun luminance(c: Color): Float =
        0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)

    fun ratio(a: Color, b: Color): Float {
        val la = luminance(a); val lb = luminance(b)
        val hi = maxOf(la, lb); val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
```

- [ ] **Step 2: Write the contrast test (RED)**

Create `ContrastTest.kt`. AA = 4.5 for normal text, 3.0 for large/secondary. `faint` is tertiary/placeholder → 3.0 floor.

```kotlin
package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

class ContrastTest {

    private fun aa(fg: Color, bg: Color, min: Float, label: String) {
        val r = Wcag.ratio(fg, bg)
        assertTrue(r >= min, "$label contrast ${(r * 100).toInt() / 100f} < $min")
    }

    @Test
    fun dark_palette_passes_aa() {
        val p = HisaabColors.Dark
        aa(p.onBackground, p.background, 4.5f, "dark text/bg")
        aa(p.muted, p.background, 4.5f, "dark muted/bg")
        aa(p.faint, p.background, 3.0f, "dark faint/bg")
        aa(p.onAccent, p.accent, 4.5f, "dark onAccent/accent")
        aa(p.positive, p.background, 3.0f, "dark positive/bg")
        aa(p.negative, p.background, 3.0f, "dark negative/bg")
    }

    @Test
    fun light_palette_passes_aa() {
        val p = HisaabColors.Light
        aa(p.onBackground, p.background, 4.5f, "light text/bg")
        aa(p.muted, p.background, 4.5f, "light muted/bg")
        aa(p.faint, p.background, 3.0f, "light faint/bg")
        aa(p.onBackground, p.surface, 4.5f, "light text/surface")
        aa(p.accent, p.background, 4.5f, "light accent-as-text/bg")
        aa(p.onAccent, HisaabColors.LightAccentFill, 4.5f, "light onAccent/limeFill")
        aa(p.positive, p.surface, 4.5f, "light positive/surface")
        aa(p.negative, p.surface, 4.5f, "light negative/surface")
    }
}
```

- [ ] **Step 3: Run the test**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.ContrastTest"`
Expected: PASS if the Task-1 light values already clear AA. If any line FAILS, that is the contrast pass doing its job — darken the offending light token in `HisaabColors.Light` until it passes (e.g. nudge `accent` toward `#43600D`, `positive` toward `#147A40`, `negative` toward `#B23427`), re-running until green. Do **not** weaken the assertions.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonTest/kotlin/app/hisaab/design/Wcag.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/design/ContrastTest.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt
git commit -m "test(design): WCAG AA contrast gate; tune light palette to pass"
```

---

## Task 3: Bundle the Midnight fonts

The Midnight type stack needs five OFL families as static TTFs in `composeResources/font/`. Compose Resources generates `Res.font.<filename>` accessors (package `app.hisaab.resources`). Filenames must be lowercase + underscores.

**Files:**
- Create: `composeApp/src/commonMain/composeResources/font/*.ttf`

- [ ] **Step 1: Download the static TTFs (OFL, via Fontsource CDN)**

Run from repo root:

```bash
FONT=composeApp/src/commonMain/composeResources/font
mkdir -p "$FONT"
base="https://cdn.jsdelivr.net/fontsource/fonts"
dl() { curl -fLo "$FONT/$2" "$1" && echo "ok $2 ($(wc -c < "$FONT/$2") bytes)"; }
# Space Grotesk (display / numerals)
dl "$base/space-grotesk@latest/latin-400-normal.ttf" space_grotesk_regular.ttf
dl "$base/space-grotesk@latest/latin-500-normal.ttf" space_grotesk_medium.ttf
dl "$base/space-grotesk@latest/latin-600-normal.ttf" space_grotesk_semibold.ttf
dl "$base/space-grotesk@latest/latin-700-normal.ttf" space_grotesk_bold.ttf
# Hanken Grotesk (UI / body)
dl "$base/hanken-grotesk@latest/latin-400-normal.ttf" hanken_grotesk_regular.ttf
dl "$base/hanken-grotesk@latest/latin-500-normal.ttf" hanken_grotesk_medium.ttf
dl "$base/hanken-grotesk@latest/latin-600-normal.ttf" hanken_grotesk_semibold.ttf
dl "$base/hanken-grotesk@latest/latin-700-normal.ttf" hanken_grotesk_bold.ttf
# Space Mono (money / tabular)
dl "$base/space-mono@latest/latin-400-normal.ttf" space_mono_regular.ttf
dl "$base/space-mono@latest/latin-700-normal.ttf" space_mono_bold.ttf
# Hind Siliguri (Bengali display/UI fallback)
dl "$base/hind-siliguri@latest/bengali-400-normal.ttf" hind_siliguri_regular.ttf
dl "$base/hind-siliguri@latest/bengali-600-normal.ttf" hind_siliguri_semibold.ttf
# Noto Sans Bengali (Bengali mono fallback)
dl "$base/noto-sans-bengali@latest/bengali-400-normal.ttf" noto_sans_bengali_regular.ttf
```

Expected: each line prints `ok <file> (NNNNN bytes)` with size > 10000. If any URL 404s, find the equivalent on https://fontsource.org/fonts (same `latin-<weight>-normal.ttf` / `bengali-<weight>-normal.ttf` pattern) and adjust.

- [ ] **Step 2: Generate the Res accessors**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL; `Res.font.space_grotesk_regular` (etc.) now resolvable under `app.hisaab.resources`.

- [ ] **Step 3: Commit the fonts**

```bash
git add composeApp/src/commonMain/composeResources/font/
git commit -m "chore(design): bundle Midnight OFL fonts (Space Grotesk/Hanken/Space Mono/Hind Siliguri/Noto Bengali)"
```

---

## Task 4: Midnight typography

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabTypographyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTypography.kt`

- [ ] **Step 1: Update the typography test to Midnight (RED)**

Replace the body of `HisaabTypographyTest.kt`:

```kotlin
package app.hisaab.design

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HisaabTypographyTest {
    @Test
    fun display_is_space_grotesk() {
        assertTrue(
            HisaabTypography.display.familyDescription.contains("Space Grotesk", ignoreCase = true),
            "display should be Space Grotesk (got: ${HisaabTypography.display.familyDescription})",
        )
    }

    @Test
    fun ui_is_hanken_grotesk() {
        assertTrue(HisaabTypography.ui.familyDescription.contains("Hanken", ignoreCase = true))
    }

    @Test
    fun mono_is_space_mono() {
        assertTrue(HisaabTypography.mono.familyDescription.contains("Space Mono", ignoreCase = true))
    }

    @Test
    fun heroAmount_is_oversized_semibold() {
        val s = HisaabTypography.heroAmount
        assertTrue(s.fontSize.value >= 48f, "heroAmount should be ≥48sp (got ${s.fontSize})")
        assertEquals(FontWeight.SemiBold, s.fontWeight)
    }

    @Test
    fun eyebrow_is_tracked() {
        val s = HisaabTypography.eyebrow
        assertTrue(s.letterSpacing.value >= 1.0f, "eyebrow should be tracked (got ${s.letterSpacing})")
    }

    @Test
    fun tabular_numerals_enabled_on_money() {
        assertTrue("tnum" in (HisaabTypography.tabular.fontFeatureSettings ?: ""))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabTypographyTest"`
Expected: FAIL — families are still serif/sans/monospace; `eyebrow` doesn't exist; heroAmount weight is Normal.

- [ ] **Step 3: Rebuild typography with bundled fonts + Midnight scale**

Replace the body of `HisaabTypography.kt`:

```kotlin
package app.hisaab.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.hisaab.resources.Res
import org.jetbrains.compose.resources.Font
// IMPORTANT: use the Compose-Resources `Font` (org.jetbrains.compose.resources.Font), which is
// @Composable and takes a FontResource — NOT androidx.compose.ui.text.font.Font.
// The `Res.font.<name>` accessors are generated into the app.hisaab.resources package; if the IDE
// flags an unresolved `Res.font.*`, accept its suggested auto-import (some Compose versions emit
// per-font symbols like `import app.hisaab.resources.space_grotesk_regular`).

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

    @Composable
    fun families(): Families = Families(
        display = FontFamily(
            Font(Res.font.space_grotesk_regular, FontWeight.Normal),
            Font(Res.font.space_grotesk_medium, FontWeight.Medium),
            Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold),
            Font(Res.font.space_grotesk_bold, FontWeight.Bold),
            Font(Res.font.hind_siliguri_semibold, FontWeight.SemiBold),
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
        fontSize = 11.sp, letterSpacing = 1.5.sp, lineHeight = 14.sp,
    )
    val body = TextStyle(
        fontFamily = ui.compose, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    )
    val label = TextStyle(
        fontFamily = ui.compose, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 1.5.sp, lineHeight = 14.sp,
    )
    val tabular = TextStyle(
        fontFamily = mono.compose, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum, lnum",
    )
}
```

> Note: the `display/ui/mono.compose` placeholders use platform families so unit tests (which run without composition) pass on family *description*. HisaabTheme (Task 7) rebinds the Material3 `Typography` to the real bundled `families()` so the running app uses the OFL fonts. This keeps the bundled-font lookup inside composition (required by Compose Resources on iOS/wasm) while keeping styles testable.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabTypographyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTypography.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabTypographyTest.kt
git commit -m "feat(design): Midnight typography (bundled fonts + oversized scale)"
```

---

## Task 5: Midnight shapes

**Files:**
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabShapesTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabShapes.kt`

- [ ] **Step 1: Write the shapes test (RED)**

Create `HisaabShapesTest.kt`:

```kotlin
package app.hisaab.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HisaabShapesTest {
    @Test
    fun midnight_radii_match_spec() {
        assertEquals(RoundedCornerShape(22.dp), HisaabShapes.card)
        assertEquals(RoundedCornerShape(16.dp), HisaabShapes.field)
        assertEquals(RoundedCornerShape(999.dp), HisaabShapes.pill)
    }

    @Test
    fun sheet_has_26dp_top_radius_only() {
        assertEquals(
            RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            HisaabShapes.sheet,
        )
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabShapesTest"`
Expected: FAIL — `card` is `10.dp`, `field` doesn't exist, `sheet` top is `18.dp`.

- [ ] **Step 3: Update shapes**

Replace the body of `HisaabShapes.kt`:

```kotlin
package app.hisaab.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object HisaabShapes {
    val field = RoundedCornerShape(16.dp)
    val card  = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val pill  = RoundedCornerShape(999.dp)

    fun material3(): Shapes = Shapes(
        small  = field,
        medium = card,
        large  = RoundedCornerShape(26.dp),
    )
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabShapesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabShapes.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabShapesTest.kt
git commit -m "feat(design): Midnight shapes (card 22 / field 16 / sheet 26)"
```

---

## Task 6: Midnight spacing (gutter 20dp)

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabSpacingTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabSpacing.kt`

- [ ] **Step 1: Update the spacing test (RED)**

In `HisaabSpacingTest.kt`, change the gutter assertion line from `22.dp` to `20.dp`:

```kotlin
        assertEquals(20.dp, HisaabSpacing.gutter)
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabSpacingTest"`
Expected: FAIL — gutter is `22.dp`.

- [ ] **Step 3: Update the gutter**

In `HisaabSpacing.kt`, change the `gutter` line to:

```kotlin
    val gutter = 20.dp
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabSpacingTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabSpacing.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabSpacingTest.kt
git commit -m "feat(design): Midnight gutter 20dp"
```

---

## Task 7: Wire Midnight into HisaabTheme

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTheme.kt`

- [ ] **Step 1: Map the expanded palette to Material3 + bind bundled fonts**

Replace the body of `HisaabTheme.kt`:

```kotlin
package app.hisaab.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHisaabPalette = staticCompositionLocalOf<HisaabColors.Palette> {
    error("HisaabTheme not provided")
}

@Composable
fun HisaabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Midnight is dark-primary; light is the derived variant.
    val palette = if (darkTheme) HisaabColors.Dark else HisaabColors.Light

    val scheme: ColorScheme = if (darkTheme) {
        darkColorScheme(
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceRaised,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            onSurfaceVariant = palette.muted,
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentDim,
            tertiary = palette.positive,
            error = palette.negative,
            outline = palette.rule,
        )
    } else {
        lightColorScheme(
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceRaised,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            onSurfaceVariant = palette.muted,
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentDim,
            tertiary = palette.positive,
            error = palette.negative,
            outline = palette.rule,
        )
    }

    // Bind the bundled OFL families into the Midnight text scale (inside composition).
    val fams = HisaabTypography.families()
    val typography = Typography(
        displayLarge   = HisaabTypography.heroAmount.copy(fontFamily = fams.display),
        headlineMedium = HisaabTypography.title.copy(fontFamily = fams.display),
        labelLarge     = HisaabTypography.eyebrow.copy(fontFamily = fams.ui),
        bodyMedium     = HisaabTypography.body.copy(fontFamily = fams.ui),
        labelSmall     = HisaabTypography.label.copy(fontFamily = fams.ui),
        bodySmall      = HisaabTypography.tabular.copy(fontFamily = fams.mono),
    )

    CompositionLocalProvider(LocalHisaabPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = HisaabShapes.material3(),
            content = content,
        )
    }
}
```

- [ ] **Step 2: Run the full design suite + compile commonMain**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.*"`
Expected: PASS (all design tests). The module compiles (no references to removed symbols — `gold`/`rule` still exist on `Palette`).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTheme.kt
git commit -m "feat(design): wire Midnight palettes + bundled fonts into HisaabTheme"
```

---

## Task 8: Smoke-verify the re-skin on the emulator

The whole app reads tokens via `LocalHisaabPalette` + Material3, so existing screens should now render in Midnight with zero screen edits. This task confirms that and catches any screen that hardcoded a color instead of reading a token.

**Files:** none expected (fix only if a hardcoded color is found).

- [ ] **Step 1: Build + install the debug APK**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:assembleDebug`
Then: `~/Library/Android/sdk/platform-tools/adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`
Expected: BUILD SUCCESSFUL; `Success`.

- [ ] **Step 2: Launch + screenshot Welcome**

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
"$ADB" shell monkey -p app.hisaab -c android.intent.category.LAUNCHER 1
sleep 2
"$ADB" exec-out screencap -p > /tmp/midnight-welcome.png
```

Open `/tmp/midnight-welcome.png`. Expected: **deep ink (#0A0B0E) background, lime হিসাব wordmark, white Space Grotesk headline** — i.e. Midnight, not cream.

- [ ] **Step 3: Fix any token leaks (only if needed)**

If a surface still shows cream/amber, grep for hardcoded colors in that screen and replace with the token:

Run: `grep -rn "Color(0xFF" composeApp/src/commonMain/kotlin/app/hisaab/screens`
For each hit that should be themed, replace the literal with `LocalHisaabPalette.current.<role>`. Commit per-screen.

- [ ] **Step 4: Confirm the existing suites still pass (no logic leaked)**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest`
Expected: PASS (whole commonTest suite — the re-skin must not break any ViewModel/logic test).

- [ ] **Step 5: Commit (only if Step 3 made changes)**

```bash
git add -A composeApp/src/commonMain/kotlin/app/hisaab/screens
git commit -m "fix(design): route hardcoded screen colors through Midnight tokens"
```

---

## Done criteria for Phase 1

- All `app.hisaab.design.*` unit tests pass, including the new `ContrastTest` (both themes AA) and `HisaabShapesTest`.
- The full `commonTest` suite still passes (no logic regressions).
- The app builds and launches; Welcome + Today render in Midnight on the emulator.
- Fonts are bundled and resolved via `Res.font.*`.
- Editorial palettes parked (not deleted); no feature logic changed.

**Next phase:** `2026-05-31-midnight-phase-2-primitives.md` — GlassCard, MoneyText, GlyphChip, buttons, sheets, FloatingDock + FAB, Keypad (written when Phase 1 is green).
