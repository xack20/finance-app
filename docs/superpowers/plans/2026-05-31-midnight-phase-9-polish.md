# Midnight Redesign — Phase 9: Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close out the Midnight redesign with cross-cutting polish: reduced-motion accessibility gating, a both-theme WCAG-AA contrast pass (with one token fix), design-token consolidation, a `MidnightDialog` content slot (dropping the last raw `AlertDialog`), dead-palette removal, and a dark-Midnight screenshot regen.

**Architecture:** Each task is an independent, mostly-mechanical change verified by build + the existing `ContrastTest` / screenshot harness. No ViewModel/repository/crypto/data logic changes. Animations gate behind a new `LocalReduceMotion` flag; the only behavioral change is that motion stops when the OS "reduce motion" setting is on.

**Tech Stack:** KMP + Compose Multiplatform 1.10.1; `app.hisaab.design` system. New cross-platform `expect/actual` for the reduce-motion query (mirrors the existing `PlatformInfo` pattern).

**Build command (verified):** `export JAVA_HOME=/Users/xack/.sdkman/candidates/java/current && ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest --console=plain`

---

## Hard constraints (apply to EVERY task)

1. **Touch only the files named in the task.** No ViewModel/repository/container/crypto/db change.
2. **Use palette role tokens, never hardcoded hex** (except the deliberate `HisaabColors` token-value edits in Task 3).
3. **The "leave-raw" shape/spacing set in Task 4/5 must NOT be touched** — those radii/widths have no matching token and are intentional one-offs.
4. **`rule`, `gold`, `LightAccentFill` palette roles must STAY** — they have live consumers; only the parked `Editorial*` palettes are dead (Task 7).
5. Keep each task in its own commit so visual deltas (esp. the 22→20dp gutter and the dark screenshot flip) are reviewable in isolation.

---

## Task 1: Reduced-motion infrastructure

**Files:** Create `composeApp/src/commonMain/kotlin/app/hisaab/design/ReduceMotion.kt`, `composeApp/src/androidMain/kotlin/app/hisaab/design/ReduceMotion.android.kt`, `composeApp/src/iosMain/kotlin/app/hisaab/design/ReduceMotion.ios.kt`, `composeApp/src/wasmJsMain/kotlin/app/hisaab/design/ReduceMotion.wasmJs.kt`; Modify `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTheme.kt`.

A `@Composable expect fun` (so the Android actual can read `LocalContext` with no process-wide holder) + a `LocalReduceMotion` CompositionLocal provided by `HisaabTheme`.

- [ ] **Step 1: commonMain `ReduceMotion.kt`:**

```kotlin
package app.hisaab.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** True when the OS "reduce motion" / "remove animations" accessibility setting is on.
 *  @Composable so the Android actual can read LocalContext without a process-wide holder. */
@Composable
expect fun isReduceMotionEnabled(): Boolean

/** App-wide reduce-motion flag, provided by HisaabTheme. Default false = motion on (safe fallback). */
val LocalReduceMotion = staticCompositionLocalOf { false }
```

- [ ] **Step 2: androidMain `ReduceMotion.android.kt`:**

```kotlin
package app.hisaab.design

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun isReduceMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
```

- [ ] **Step 3: iosMain `ReduceMotion.ios.kt`:**

```kotlin
package app.hisaab.design

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun isReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()
```

- [ ] **Step 4: wasmJsMain `ReduceMotion.wasmJs.kt`:**

```kotlin
package app.hisaab.design

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun isReduceMotionEnabled(): Boolean =
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
```

(If `kotlinx.browser.window` is not resolvable in wasmJsMain, use the import already used by `wasmJsMain/.../main.kt` — read that file to match.)

- [ ] **Step 5: HisaabTheme.kt** — add the provider. Find the existing `CompositionLocalProvider(LocalHisaabPalette provides palette) { ... }` and change it to:

```kotlin
CompositionLocalProvider(
    LocalHisaabPalette provides palette,
    LocalReduceMotion provides isReduceMotionEnabled(),
) {
    // ...existing MaterialTheme(...) body unchanged...
}
```

- [ ] **Step 6: Compile all targets** (actuals must exist for each): `export JAVA_HOME=/Users/xack/.sdkman/candidates/java/current && ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs :composeApp:compileKotlinIosArm64 --console=plain 2>&1 | tail -15`. Expected BUILD SUCCESSFUL. (If the iOS toolchain is unavailable on this machine, at minimum Android + wasmJs must pass AND the `ReduceMotion.ios.kt` actual must exist; note the fallback.)
- [ ] **Step 7: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/ReduceMotion.kt composeApp/src/androidMain/kotlin/app/hisaab/design/ReduceMotion.android.kt composeApp/src/iosMain/kotlin/app/hisaab/design/ReduceMotion.ios.kt composeApp/src/wasmJsMain/kotlin/app/hisaab/design/ReduceMotion.wasmJs.kt composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTheme.kt && git commit -m "feat(design): reduce-motion query + LocalReduceMotion (expect/actual)"`

---

## Task 2: Gate the animation sites behind LocalReduceMotion

**Files:** Modify `screens/SplashScreen.kt`, `screens/agent/AssistantAtoms.kt`, `design/components/FormControls.kt`, `design/components/MidnightTextField.kt`.

Each animation reads `LocalReduceMotion.current`; when true, render at the resting/target value with NO transition object created (no rAF churn).

- [ ] **Step 1: SplashScreen.kt `SplashPulseDots()`** — when `LocalReduceMotion.current`, render the three dots at a static alpha (e.g. `1f`) and skip `rememberInfiniteTransition`/`infiniteRepeatable` entirely. Otherwise keep the existing pulse.
- [ ] **Step 2: AssistantAtoms.kt `TypingDots()`** — reduced → three dots at a static alpha (e.g. `0.6f`), no infinite transition. `ListeningEqualizer()` — reduced → five bars at a static mid height (so it still reads as "listening"), no infinite transition. Keep the non-reduced paths unchanged.
- [ ] **Step 3: FormControls.kt `HToggle()`** — replace `val knobX by animateDpAsState(...)` with: `val target = if (checked) 23.dp else 3.dp; val knobX = if (LocalReduceMotion.current) target else animateDpAsState(target, label = "toggleKnob").value` (knob snaps when reduced).
- [ ] **Step 4: MidnightTextField.kt** — extract the target border `val targetBorder = when { isError -> p.negative; focused -> p.accent; else -> p.hair }`, then `val border = if (LocalReduceMotion.current) targetBorder else animateColorAsState(targetBorder, label = "fieldBorder").value` (border snaps when reduced).
- [ ] **Step 5: Compile + full suite.** Build command → BUILD SUCCESSFUL / PASS.
- [ ] **Step 6: Grep regression.** `grep -rn "rememberInfiniteTransition\|animateDpAsState\|animateColorAsState" composeApp/src/commonMain/kotlin/app/hisaab` — confirm every match is inside an `if (LocalReduceMotion.current)`-guarded branch (the 5 sites above). (MidnightSlider's internal Material3 thumb animation is accepted residual — do not touch.)
- [ ] **Step 7: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/SplashScreen.kt composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/AssistantAtoms.kt composeApp/src/commonMain/kotlin/app/hisaab/design/components/FormControls.kt composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightTextField.kt && git commit -m "feat(a11y): gate splash/typing/equalizer/toggle/field animations on reduce-motion"`

---

## Task 3: Contrast — Dark.faint token fix + ContrastTest assertions

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt`, `composeApp/src/commonTest/kotlin/app/hisaab/design/ContrastTest.kt`.

Dark `faint` (#5C6470) fails AA against `surface` (2.94) and `surfaceRaised` (2.69) — both below the 3.0 UI floor — yet Phase 7/8 put faint placeholder/disabled text on those surfaces. Fix the token, then add the missing both-theme assertions. **Do this token+tests change together so the suite stays green.**

- [ ] **Step 1: HisaabColors.kt** — change the Dark palette `faint = Color(0xFF5C6470)` to `faint = Color(0xFF737B88)`. (New ratios: faint/surface 4.11, faint/surfaceRaised 3.77, faint/background 4.61 — all pass; existing 3.0 test still passes.) Leave Light `faint` (#79828F) unchanged.
- [ ] **Step 2: ContrastTest.kt** — add a top-level (or private) alpha-flatten helper:

```kotlin
/** Flatten a translucent fg token over an opaque bg, matching how Compose composites it. */
private fun flatten(fg: Color, bg: Color): Color = Color(
    red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
    green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
    blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
    alpha = 1f,
)
```

- [ ] **Step 3: ContrastTest.kt `dark_palette_passes_aa`** — add these assertions (placeholders/disabled at the project's 3.0 UI tier):

```kotlin
val glassBg = flatten(p.glass, p.background)
val bioBg = flatten(p.accentSoft, p.background)
aa(p.faint, p.surface, 3.0f, "dark placeholder faint/surface")
aa(p.faint, p.surfaceRaised, 3.0f, "dark disabled-label faint/surfaceRaised")
aa(p.onBackground, p.surface, 4.5f, "dark input/otp/word text onBackground/surface")
aa(p.accent, p.surface, 4.5f, "dark mono-prefix/recovery-index accent/surface")
aa(p.accent, p.surface, 3.0f, "dark otp active border accent/surface (UI)")
aa(p.negative, p.surface, 4.5f, "dark negative text/surface")
aa(p.onBackground, glassBg, 4.5f, "dark glass-button label/glass-over-bg")
aa(p.accent, bioBg, 3.0f, "dark biometric glyph accent/accentSoft-over-bg")
```

- [ ] **Step 4: ContrastTest.kt `light_palette_passes_aa`** — add the light mirror (uses `p` = Light palette in that test):

```kotlin
val glassBgL = flatten(p.glass, p.background)
val bioBgL = flatten(p.accentSoft, p.background)
aa(p.faint, p.surface, 3.0f, "light placeholder faint/surface")
aa(p.faint, p.surfaceRaised, 3.0f, "light disabled-label faint/surfaceRaised")
aa(p.accent, p.surface, 4.5f, "light mono-prefix/recovery-index accent/surface")
aa(p.accent, p.surface, 3.0f, "light otp active border accent/surface (UI)")
aa(p.onBackground, glassBgL, 4.5f, "light glass-button label/glass-over-bg")
aa(p.accent, bioBgL, 3.0f, "light biometric glyph accent/accentSoft-over-bg")
```

(Do NOT add an `onAccent`-on-`accent` light button assertion — the light button fill is `LightAccentFill`, already covered by the existing line-36 assertion.)

- [ ] **Step 5: Run the contrast suite.** Build command (or `./gradlew :composeApp:testDebugUnitTest --tests "*ContrastTest*" --console=plain`) → both `dark_palette_passes_aa` and `light_palette_passes_aa` PASS.
- [ ] **Step 6: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt composeApp/src/commonTest/kotlin/app/hisaab/design/ContrastTest.kt && git commit -m "fix(a11y): bump Dark.faint to AA on surface + add field/cell/button contrast cases"`

---

## Task 4: Spacing — pill/card/field token swaps (lossless)

**Files:** Modify the screen files listed below (add `import app.hisaab.design.HisaabShapes` where missing).

All exact-match radius swaps — zero visual change. Replace `RoundedCornerShape(999.dp)` → `HisaabShapes.pill` at these 10 sites: `agent/AgentScreen.kt:382`, `agent/AssistantAtoms.kt:91`, `agent/AssistantAtoms.kt:93`, `entry/EntryKeypad.kt:77`, `entry/EntryKeypad.kt:79`, `month/CategoryBarChart.kt:41` (the local `val pill`), `month/MonthScreen.kt:95`, `people/PersonDetailScreen.kt:209`, `today/ReviewBadge.kt:28`, `today/TodayScreen.kt:126` and `today/TodayScreen.kt:134`. Plus: `today/TodayScreen.kt:180` `RoundedCornerShape(22.dp)` → `HisaabShapes.card`; `entry/EntryKeypad.kt:112` `RoundedCornerShape(16.dp)` → `HisaabShapes.field`.

**LEAVE RAW (do NOT touch):** `LockScreen.kt:89` (26.dp tile), `ReviewInboxScreen.kt` 14.dp (3 sites), `CaptureOptInCard.kt:43` (13.dp), `BiometricSetupScreen.kt:58` (28.dp), `PersonDetailScreen.kt:176` (11.dp), `CategoriesScreen.kt:70` (10.dp), `TodayScreen.kt:282` (4.dp), `AssistantAtoms.kt:38,74` (computed/2.dp), `AgentScreen.kt:249,256,296` (asymmetric bubbles).

- [ ] **Step 1: Apply the swaps** at the exact lines above. Verify each touched file imports `HisaabShapes`; add the import if missing. For `CategoryBarChart.kt:41`, set `val pill = HisaabShapes.pill` (minimal diff). (Line numbers are guidance — match on the `RoundedCornerShape(999.dp)` text in case lines shifted.)
- [ ] **Step 2: Compile + full suite.** Build command → PASS.
- [ ] **Step 3: Leak grep.** `grep -rn "RoundedCornerShape(999" composeApp/src/commonMain/kotlin/app/hisaab/screens` → zero hits. `grep -rn "RoundedCornerShape(16.dp)" composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryKeypad.kt` → zero hits.
- [ ] **Step 4: Commit.** `git add -A composeApp/src/commonMain/kotlin/app/hisaab/screens && git commit -m "refactor(design): consolidate pill/card/field shapes to HisaabShapes tokens"`

---

## Task 5: Spacing — 22.dp gutters → HisaabSpacing.gutter (−2dp)

**Files:** Modify `entry/EntryFields.kt`, `entry/EntryScreen.kt`, `entry/SplitEditorSheet.kt`, `recovery/RecoveryEntryScreen.kt`, `settings/AutoCaptureScreen.kt`, `settings/CloudConsentScreen.kt`, `settings/RecoveryPhraseRevealScreen.kt`, `settings/SettingsScreen.kt`, `transaction/TransactionDetailScreen.kt`.

This is a deliberate **−2dp** standardization (22→20dp `gutter`), kept in its own commit. Replace the screen/sheet gutter padding at: `EntryFields.kt:117`, `EntryFields.kt:182`, `EntryScreen.kt:310`, `SplitEditorSheet.kt:67` (`.padding(22.dp)` → `.padding(HisaabSpacing.gutter)`); `RecoveryEntryScreen.kt:77`, `AutoCaptureScreen.kt:102`, `CloudConsentScreen.kt:61`, `RecoveryPhraseRevealScreen.kt:70`, `SettingsScreen.kt:48`, `TransactionDetailScreen.kt:93` (`.padding(horizontal = 22.dp)` → `.padding(horizontal = HisaabSpacing.gutter)`).

**LEAVE RAW:** `RecoveryPhraseRevealScreen.kt:124` `Modifier.width(22.dp)` — this is an element WIDTH, not a gutter. Do NOT rewrite it.

- [ ] **Step 1: Apply the 10 gutter swaps.** Verify each file imports `HisaabSpacing`; add if missing.
- [ ] **Step 2: Compile + full suite.** Build command → PASS.
- [ ] **Step 3: Leak grep.** `grep -rn "padding(22.dp)\|horizontal = 22.dp" composeApp/src/commonMain/kotlin/app/hisaab/screens` → zero hits (the `width(22.dp)` site is excluded since it's not a padding).
- [ ] **Step 4: Commit.** `git add -A composeApp/src/commonMain/kotlin/app/hisaab/screens && git commit -m "refactor(design): 22dp gutters → HisaabSpacing.gutter (−2dp standardization)"`

---

## Task 6: MidnightDialog content slot + AccountsScreen rename

**Files:** Modify `design/components/MidnightDialog.kt`, `screens/settings/AccountsScreen.kt`.

Add a `confirmEnabled` param and an optional `content` slot, then drop the last raw `AlertDialog` (AccountsScreen's RenameAccountDialog).

- [ ] **Step 1: Read both files.** Note MidnightDialog's current params and that both call sites (`SettingsScreen.kt` sign-out, AccountsScreen rename) use named args.
- [ ] **Step 2: Extend MidnightDialog.kt** — keep `body` in its original position (defaulted `= ""`), add `confirmEnabled` + `content`:

```kotlin
@Composable
fun MidnightDialog(
    onDismiss: () -> Unit,
    title: String,
    body: String = "",
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val p = LocalHisaabPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.onBackground,
        textContentColor = p.muted,
        shape = HisaabShapes.card,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column {
                if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium)
                if (content != null) {
                    if (body.isNotEmpty()) Spacer(Modifier.height(HisaabSpacing.md))
                    content()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmLabel, color = when {
                    !confirmEnabled -> p.muted
                    destructive -> p.negative
                    else -> p.accent
                })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = p.muted) } },
    )
}
```

Add imports as needed: `androidx.compose.foundation.layout.Column`, `ColumnScope`, `Spacer`, `height`, `app.hisaab.design.HisaabSpacing` (and keep existing `Modifier`, `HisaabShapes`, `MaterialTheme`, `Text`, `TextButton`, `AlertDialog`). Match the existing body/title styles if they differ from the sketch — preserve current sign-out dialog appearance.

- [ ] **Step 3: Rewrite AccountsScreen RenameAccountDialog** — replace the raw `AlertDialog` with:

```kotlin
@Composable
private fun RenameAccountDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    MidnightDialog(
        onDismiss = onDismiss,
        title = "Rename account",
        confirmLabel = "Save",
        onConfirm = { onSave(name.trim()) },
        confirmEnabled = name.isNotBlank() && name.trim() != current,
    ) {
        MidnightTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

Preserve the exact title/labels the original used (match them if they differ from "Rename account"/"Save"). The old gate (`name.isNotBlank() && name != current`) becomes `confirmEnabled` (button is now visibly disabled instead of silent no-op). Remove the now-unused local palette val / raw `AlertDialog`/`OutlinedTextField` imports if they become unused.

- [ ] **Step 4: Compile + full suite.** Build command → PASS.
- [ ] **Step 5: Grep.** `grep -n "AlertDialog" composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AccountsScreen.kt` → no hits. `grep -rn "MidnightDialog" composeApp/src` → exactly the 2 call sites.
- [ ] **Step 6: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightDialog.kt composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AccountsScreen.kt && git commit -m "feat(design): MidnightDialog content slot + confirmEnabled; rename dialog drops raw AlertDialog"`

---

## Task 7: Dead-code — remove parked Editorial palettes

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt`.

- [ ] **Step 1: Pre-check.** `grep -rn "EditorialLight\|EditorialDark" composeApp/src` → only the two definition lines (zero external references).
- [ ] **Step 2: Delete** the parked block — the `// --- Parked: original "Editorial Premium" palettes ... ---` comment through the closing `)` of `EditorialDark` (≈ lines 106–126). Leave `rule`, `gold`, `LightAccentFill`, `categoryHues`, Dark, Light, and the object's closing brace intact.
- [ ] **Step 3: Compile + full suite.** Build command → PASS (ContrastTest still resolves `LightAccentFill`).
- [ ] **Step 4: Verify live roles untouched.** `grep -rn "palette\.rule\|palette\.gold" composeApp/src` → still returns all consumer hits (12 rule + 1 gold). `grep -rn "EditorialLight\|EditorialDark" composeApp/src` → zero.
- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt && git commit -m "chore(design): remove parked Editorial palettes (dead code)"`

---

## Task 8: Screenshot harness — dark Midnight regen + splash

**Files:** Modify `composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/ui/ScreenshotTourTest.kt`, `composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/ui/ScreenshotVmTest.kt`, `docs/design/screenshots/android/README.md`; regen the PNGs under `docs/design/screenshots/android/`.

The tours currently render `HisaabTheme(darkTheme = false)` — the LIGHT variant — but the app ships dark-primary Midnight. Flip to dark, add the splash, and regenerate so the docs match the shipped UI. **This is the one visually significant change in Phase 9 (≈36 PNGs go light→dark).**

- [ ] **Step 1: Flip both tours to dark.** `ScreenshotTourTest.kt:219` and `ScreenshotVmTest.kt:213` `HisaabTheme(darkTheme = false)` → `HisaabTheme(darkTheme = true)`.
- [ ] **Step 2: Add splash** to `ScreenshotTourTest.kt`'s stateless `pages` list: `"00-splash" to { SplashScreen() }` and `import app.hisaab.screens.SplashScreen`. (SplashScreen takes no params/container.) Do NOT add Lock/RecoveryEntry to the stateless tour — they require `LocalAppContainer` and already render in the VM tour as `25-lock` / `26-recovery-entry`.
- [ ] **Step 3: Compile androidTest.** `export JAVA_HOME=/Users/xack/.sdkman/candidates/java/current && ./gradlew :composeApp:compileDebugAndroidTestSources --console=plain 2>&1 | tail -8` → BUILD SUCCESSFUL.
- [ ] **Step 4: Controller regen on the emulator** (best-effort; the code flip + a successful run together form the deliverable). Wake the emulator first (`adb -s emulator-5554 shell svc power stayon true; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard`; confirm `mWakefulness=Awake` — a black ~7.9KB frame means it was asleep). Then:
  - `./gradlew :composeApp:installDebug :composeApp:installDebugAndroidTest`
  - `adb -s emulator-5554 shell am instrument -w -e class app.hisaab.ui.ScreenshotTourTest,app.hisaab.ui.ScreenshotVmTest app.hisaab.test/androidx.test.runner.AndroidJUnitRunner` (expect OK)
  - `adb -s emulator-5554 pull /sdcard/Android/data/app.hisaab/files/screenshots/. docs/design/screenshots/android/`
  - Spot-check `00-splash.png`, `25-lock.png`, `26-recovery-entry.png` render dark Midnight (HisaabColors.Dark).
- [ ] **Step 5: README** — update `docs/design/screenshots/android/README.md` line 1 from "Light-theme" to dark Midnight; add a `00-splash.png` row.
- [ ] **Step 6: Commit.** `git add composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/ui/ScreenshotTourTest.kt composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/ui/ScreenshotVmTest.kt docs/design/screenshots/android/ && git commit -m "test(screenshots): render dark Midnight + add splash; regen PNGs"`

If the emulator instrumented run is uncooperative, commit the code edits (dark flip + splash + README) alone and note that PNG regen is pending — do NOT commit a stale light/dark mismatch.

---

## Task 9: Final verification + review

- [ ] **Step 1: Full sanity gate.** `export JAVA_HOME=/Users/xack/.sdkman/candidates/java/current && ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest :composeApp:compileDebugAndroidTestSources --console=plain 2>&1 | tail -15` → BUILD SUCCESSFUL + tests pass.
- [ ] **Step 2: Controller dispatches a final cross-cutting review** (files-touched audit confirming no ViewModel/crypto file; reduce-motion gating complete; ContrastTest green in both themes; no leftover `RoundedCornerShape(999`/`padding(22.dp)`; no raw `AlertDialog` in AccountsScreen; Editorial palettes gone; `rule`/`gold`/`LightAccentFill` intact).
- [ ] **Step 3: Then `superpowers:finishing-a-development-branch`** to integrate the whole Midnight redesign branch.

---

## Done criteria for Phase 9

- A `LocalReduceMotion` flag exists with per-platform actuals; the splash, typing, equalizer, toggle, and field-focus animations all freeze when the OS reduce-motion setting is on.
- `ContrastTest` asserts the new field/cell/button/tile pairings in BOTH themes and passes; Dark `faint` meets the 3.0 UI floor on every surface.
- All pill/card/field shapes use `HisaabShapes`; all screen/sheet gutters use `HisaabSpacing.gutter`; the intentional leave-raw set is untouched.
- `MidnightDialog` has a content slot; AccountsScreen's rename uses it and no raw `AlertDialog` remains in the app's screens.
- The parked Editorial palettes are removed; `rule`/`gold`/`LightAccentFill` remain.
- Screenshots render the shipped dark Midnight palette and include the splash.
- Full `commonTest` suite green; `compileDebugAndroidTestSources` green.

**This completes the Midnight redesign** (Phases 1–9). Remaining follow-ups (out of scope, track separately): migrate the last `palette.rule`/`palette.gold` consumers so those legacy roles can be deleted; a fully-reactive reduce-motion observer (current reads once at theme composition); optional light-variant screenshot set.
