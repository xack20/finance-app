# Midnight Redesign — Phase 2: Component Primitives — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the reusable Midnight component primitives (money text, glass cards, glyph chips, eyebrow/section headers, buttons) under a new `design/components/` package, so the screen-port phases compose from one consistent toolkit.

**Architecture:** New package `app.hisaab.design.components`. Primitives read tokens from `LocalHisaabPalette` + `MaterialTheme` (Phase 1). Pure formatting logic (Taka grouping) extends the existing `app.hisaab.util.Money` and is unit-tested (TDD). Visual composables are verified by compilation plus a debug "component gallery" screenshot on the emulator (per the project's web-testing rule: visual regression carries more signal than brittle markup assertions for highly visual components).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.10.1, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-05-31-midnight-redesign-design.md` (§6 component primitives). **Design source:** `docs/design/design_handoff_hisaab_midnight/README.md` (§ tokens + per-component specs) and `src/neo.jsx` / `src/neo-ui.jsx`.

**Scope:** Display/form primitives only. Navigation/interaction primitives (FloatingDock + FAB chooser, Keypad) and modal Sheet/Dialog are Phase 3 (shell) where they're wired with state. Do NOT port screens here.

---

## File map

- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/util/Money.kt` — add `Long.grouped()` + `Double.toTaka(...)`.
- Modify: `composeApp/src/commonTest/kotlin/app/hisaab/util/MoneyTest.kt` (create if absent) — Taka formatting tests.
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/MoneyText.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/SurfaceCard.kt` (GlassCard + SurfaceCard)
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/GlyphChip.kt` (+ category-hue resolver)
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/TextBits.kt` (Eyebrow, SectionHeader)
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/Buttons.kt` (PrimaryButton, GlassButton)
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt` (debug-only gallery for screenshot verification)

**Test command:** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.util.MoneyTest"` (fall back to `/usr/libexec/java_home`).

---

## Task 1: Taka formatter (grouped thousands + ৳ + sign)

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/app/hisaab/util/MoneyTest.kt` (create if it doesn't exist)
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/util/Money.kt`

- [ ] **Step 1: Write the failing tests (RED).** Create/replace `MoneyTest.kt`:

```kotlin
package app.hisaab.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    @Test fun groups_thousands_western_style() {
        assertEquals("0", 0L.grouped())
        assertEquals("999", 999L.grouped())
        assertEquals("1,200", 1200L.grouped())
        assertEquals("62,250", 62250L.grouped())
        assertEquals("1,234,567", 1234567L.grouped())
    }

    @Test fun taka_unsigned_whole() {
        assertEquals("৳1,200", 1200.0.toTaka())
        assertEquals("৳0", 0.0.toTaka())
    }

    @Test fun taka_negative_uses_minus_sign_and_symbol() {
        assertEquals("−৳1,250", (-1250.0).toTaka()) // U+2212 MINUS
    }

    @Test fun taka_signed_shows_plus_for_positive() {
        assertEquals("+৳62,250", 62250.0.toTaka(signed = true))
        assertEquals("−৳1,250", (-1250.0).toTaka(signed = true))
        assertEquals("৳0", 0.0.toTaka(signed = true)) // zero is never signed
    }

    @Test fun taka_with_decimals_groups_whole_only() {
        assertEquals("৳1,200.50", 1200.5.toTaka(decimals = 2))
    }
}
```

- [ ] **Step 2: Run to verify it fails.**
`... --tests "app.hisaab.util.MoneyTest"` → FAIL (`grouped`/`toTaka` unresolved).

- [ ] **Step 3: Implement.** Append to `composeApp/src/commonMain/kotlin/app/hisaab/util/Money.kt` (keep the existing `toMoneyString`):

```kotlin
private const val TAKA = "৳"   // ৳  BENGALI RUPEE SIGN
private const val MINUS = "−"  // −  MINUS SIGN

/** Group a non-negative integer magnitude with Western thousands separators: 62250 -> "62,250". */
fun Long.grouped(): String {
    val s = (if (this < 0) -this else this).toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    val lead = s.length % 3
    var i = 0
    if (lead > 0) { sb.append(s, 0, lead); i = lead; if (i < s.length) sb.append(',') }
    while (i < s.length) {
        sb.append(s, i, i + 3)
        i += 3
        if (i < s.length) sb.append(',')
    }
    return sb.toString()
}

/**
 * Format an amount as Taka for display: grouped thousands, ৳ mark, optional sign.
 *  - `1200.0.toTaka()` -> "৳1,200"
 *  - `(-1250.0).toTaka()` -> "−৳1,250" (U+2212 minus)
 *  - `62250.0.toTaka(signed = true)` -> "+৳62,250"; zero is never signed
 * Magnitude is rounded to [decimals] (default 0 — paisa is rarely shown in BD UI) via [toMoneyString].
 */
fun Double.toTaka(signed: Boolean = false, decimals: Int = 0): String {
    val mag = kotlin.math.abs(this).toMoneyString(decimals) // "1200" or "1200.50", unsigned
    val dot = mag.indexOf('.')
    val whole = (if (dot >= 0) mag.substring(0, dot) else mag).toLong()
    val body = if (dot >= 0) "${whole.grouped()}${mag.substring(dot)}" else whole.grouped()
    val isZero = mag.all { it == '0' || it == '.' }
    val sign = when {
        this < 0 && !isZero -> MINUS
        signed && !isZero -> "+"
        else -> ""
    }
    return "$sign$TAKA$body"
}
```

- [ ] **Step 4: Run to verify it passes.** `... --tests "app.hisaab.util.MoneyTest"` → PASS.

- [ ] **Step 5: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/util/Money.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/util/MoneyTest.kt
git commit -m "feat(util): Taka formatter — grouped thousands + ৳ + sign"
```

---

## Task 2: MoneyText composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/MoneyText.kt`

- [ ] **Step 1: Implement.** Renders an amount in the mono tabular style, colored by sign.

```kotlin
package app.hisaab.design.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.util.toTaka

/** How a [MoneyText] colors itself by amount sign. */
enum class MoneyTone { Auto, Plain, Accent }

/**
 * Tabular money figure (Space Mono) with the ৳ mark, grouped thousands, and an optional sign.
 * [tone] = Auto colors positive=positive, negative=negative; Plain uses the inherited text color.
 */
@Composable
fun MoneyText(
    amount: Double,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    decimals: Int = 0,
    tone: MoneyTone = MoneyTone.Auto,
    style: TextStyle = MaterialTheme.typography.bodySmall, // bodySmall == HisaabTypography.tabular
) {
    val p = LocalHisaabPalette.current
    val color = when (tone) {
        MoneyTone.Accent -> p.accent
        MoneyTone.Plain -> LocalTextStyle.current.color
        MoneyTone.Auto -> when {
            amount > 0.0 -> p.positive
            amount < 0.0 -> p.negative
            else -> p.onBackground
        }
    }
    Text(text = amount.toTaka(signed = signed, decimals = decimals), modifier = modifier, style = style, color = color)
}
```

- [ ] **Step 2: Verify it compiles.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/MoneyText.kt
git commit -m "feat(design): MoneyText primitive (tabular ৳, color-by-sign)"
```

---

## Task 3: SurfaceCard + GlassCard

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/SurfaceCard.kt`

- [ ] **Step 1: Implement.**

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/**
 * The Midnight card: [surface] fill, hairline border, 22dp radius, 16dp inner padding.
 * [glass] = true uses the translucent glass fill instead of the solid surface.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    shape: RoundedCornerShape = HisaabShapes.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalHisaabPalette.current
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (glass) p.glass else p.surface, shape)
            .border(1.dp, p.hair, shape)
            .padding(HisaabSpacing.lg),
        content = content,
    )
}

/** Convenience alias for the translucent glass variant. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    SurfaceCard(modifier = modifier, glass = true, content = content)
```

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/SurfaceCard.kt
git commit -m "feat(design): SurfaceCard + GlassCard primitives"
```

---

## Task 4: GlyphChip + category-hue resolver

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/GlyphChip.kt`

- [ ] **Step 1: Implement.** A rounded square tinted with a category hue, holding an icon.

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabColors

/** Resolve a category-hue key (e.g. "violet") to its Color, falling back to slate. */
fun categoryHue(key: String?): Color =
    HisaabColors.categoryHues[key] ?: HisaabColors.categoryHues.getValue("slate")

/**
 * A 40dp rounded-square glyph chip: the [icon] rendered in the category [hue] over a 16%-alpha
 * fill of the same hue (radius 14). Used in the transaction feed, category lists, account strip.
 */
@Composable
fun GlyphChip(
    icon: ImageVector,
    hue: Color,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(hue.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = hue)
    }
}
```

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/GlyphChip.kt
git commit -m "feat(design): GlyphChip primitive + category-hue resolver"
```

---

## Task 5: Eyebrow + SectionHeader text bits

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/TextBits.kt`

- [ ] **Step 1: Implement.**

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hisaab.design.LocalHisaabPalette

/** Small uppercase tracked label in the faint color — the Midnight "eyebrow". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge, // labelLarge == HisaabTypography.eyebrow
        color = p.faint,
    )
}

/** A section header: a title in the display face, optionally preceded by an [eyebrow]. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    val p = LocalHisaabPalette.current
    Column(modifier = modifier) {
        if (eyebrow != null) Eyebrow(eyebrow)
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = p.onBackground)
    }
}
```

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/TextBits.kt
git commit -m "feat(design): Eyebrow + SectionHeader text primitives"
```

---

## Task 6: PrimaryButton + GlassButton

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/Buttons.kt`

- [ ] **Step 1: Implement.** Lime CTA (dark text on lime) + glass secondary, both pill-shaped.

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/** Full-width lime primary CTA (dark onAccent label), pill shape, 54dp tall. Disabled dims to raised surface. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = LocalHisaabPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = HisaabShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = p.onAccent,
            disabledContainerColor = p.surfaceRaised,
            disabledContentColor = p.faint,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { Text(text) }
}

/** Secondary glass button: translucent fill, hairline border, primary text color. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = LocalHisaabPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = HisaabShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.glass,
            contentColor = p.onBackground,
            disabledContainerColor = p.glass,
            disabledContentColor = p.faint,
        ),
        border = BorderStroke(1.dp, p.hair),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { Text(text) }
}
```

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/Buttons.kt
git commit -m "feat(design): PrimaryButton + GlassButton primitives"
```

---

## Task 7: Component gallery + screenshot verification

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt`

- [ ] **Step 1: Build a debug gallery composable** that renders each primitive inside `HisaabTheme`, for visual verification. Use real Material icons for the glyph chips.

```kotlin
package app.hisaab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.HisaabTheme
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.GlassCard
import app.hisaab.design.components.GlyphChip
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.MoneyTone
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SectionHeader
import app.hisaab.design.components.SurfaceCard
import app.hisaab.design.components.categoryHue

@Composable
@Preview
fun ComponentGallery() {
    HisaabTheme {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(HisaabSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HisaabSpacing.lg),
        ) {
            SectionHeader("Components", eyebrow = "Midnight")
            SurfaceCard {
                Eyebrow("Total balance")
                MoneyText(62250.0, signed = true)
                MoneyText(-1250.0)
                MoneyText(1200.0, tone = MoneyTone.Plain)
            }
            GlassCard { Text("Glass card") }
            Row(horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm)) {
                GlyphChip(Icons.Filled.ShoppingCart, categoryHue("violet"))
                GlyphChip(Icons.Filled.ShoppingCart, categoryHue("teal"))
                GlyphChip(Icons.Filled.ShoppingCart, categoryHue("amber"))
            }
            PrimaryButton("Continue", onClick = {})
            PrimaryButton("Disabled", onClick = {}, enabled = false)
            GlassButton("Maybe later", onClick = {})
        }
    }
}
```

- [ ] **Step 2: Compile + screenshot.**
  - `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL.
  - The controller (not a subagent) renders the gallery on the emulator (via a temporary launcher route or `@Preview` capture) and screenshots it to validate Midnight styling: glass cards with hairline borders + 22dp radius, lime PrimaryButton with dark label, signed money in green/red/white tabular figures, hue-tinted glyph chips. Adjust any primitive that visibly deviates from the prototype.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt
git commit -m "feat(design): component gallery for visual verification"
```

---

## Done criteria for Phase 2

- `MoneyTest` passes; the whole `commonTest` suite stays green.
- All primitive files compile (`compileDebugKotlinAndroid` BUILD SUCCESSFUL).
- The component gallery renders correctly in Midnight on the emulator (cards, money, chips, buttons match the prototype).
- No screen logic changed; primitives live under `design/components/`.

**Next phase:** `2026-05-31-midnight-phase-3-shell.md` — floating dock + center FAB chooser, Keypad, modal Sheet/Dialog, and wiring the authenticated shell (written when Phase 2 is green).
