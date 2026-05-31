# Midnight Redesign — Phase 8: Onboarding & Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-skin the full onboarding funnel (splash → welcome → OTP → biometric → recovery-phrase → profile → capture opt-in) plus the lock/restore lifecycle screens into the Midnight design system, as a PURE presentation change.

**Architecture:** Build three missing Midnight primitives first (`MidnightTextField`, `OtpCells`, `RecoveryWordGrid`) and extend `PrimaryButton`/`GlassButton` (loading spinner, glyphs, non-fill width). Then restyle each screen in place, preserving every ViewModel/container call, nav lambda, crypto/biometric flow, validation gate, composable signature, and user-visible string byte-identically. `OnboardingViewModel` and `OnboardingGraph` are READ-ONLY and must not be touched.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform 1.10.1; `app.hisaab.design` system (HisaabColors.Palette, HisaabShapes, HisaabSpacing, HisaabTypography); existing primitives in `design/components/`.

**Build command (verified):** `export JAVA_HOME=/Users/xack/.sdkman/candidates/java/current && ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest --console=plain`

---

## Hard constraints (apply to EVERY task)

1. **Touch only the file(s) named in the task.** NEVER edit `OnboardingViewModel.kt`, `OnboardingGraph.kt`, `App.kt`, `AppViewModel`, any repository/container/crypto/biometric/db file.
2. **Composable signatures are frozen.** `ScreenshotTourTest.kt` calls `WelcomeScreen(onSendOtp, isLoading, error)`, `OtpScreen(phone, onVerify, onResend, isLoading, error)`, `BiometricSetupScreen(isAvailable, onEnroll, onSkip, isLoading, error)`, `RecoveryPhraseScreen(words, onAcknowledged)`, `ProfileSetupScreen(onComplete, isLoading)`, `CaptureOptInCard(onTurnOn, onMaybeLater)`. Do NOT add/remove/reorder/rename a single parameter on these six. `SplashScreen()`, `LockScreen(onUnlock)`, `RecoveryEntryScreen(onRecovered)` signatures are also frozen.
3. **Preserve every user-visible string verbatim** (tests and copy depend on them) unless a task says otherwise.
4. **Preserve every gate/condition verbatim** (e.g. `phone.length >= 10`, `otp.length == 6`, `name.isNotBlank()`, `enabled = acknowledged`, `isAvailable && !isLoading`).
5. **Preserve every crypto/security line byte-identical**: all `secret.fill(0)`, `loadMasterSecret()`, `openDatabase()`, `storeString("biometric_enabled", ...)`, biometric prompt strings, and call ORDER.
6. **The tour renders in Light (`darkTheme = false`).** Use only `palette` role tokens — never hardcode hex — so every screen reads correctly in both themes.
7. **No new Scaffold/TopAppBar/back handler** on any onboarding/lock screen — none exist today and adding nav chrome changes behavior.

---

## Task 1: Extend PrimaryButton + GlassButton (loading, glyphs, width)

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/design/components/Buttons.kt`

Adds optional, default-valued params so every existing call site keeps compiling unchanged, while giving the CTAs an in-button spinner, leading/trailing text-glyphs, and a non-fill width for two-up rows.

- [ ] **Step 1: Replace `Buttons.kt` with the extended version** (keep package + add imports):

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/** Full-width lime primary CTA (dark onAccent label), pill shape, 54dp tall.
 *  [loading] shows an onAccent spinner in place of the label and blocks taps (fill stays lime).
 *  [leadingGlyph]/[trailingGlyph] are text glyphs (no material-icons dep). Set [fillMaxWidth]=false
 *  for two-up rows (constrain via modifier weight). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingGlyph: String? = null,
    trailingGlyph: String? = null,
    fillMaxWidth: Boolean = true,
) {
    val p = LocalHisaabPalette.current
    val widthMod = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.then(widthMod).height(54.dp),
        shape = HisaabShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = p.onAccent,
            // while loading, keep the lime fill + dark spinner; the real disabled look is faint.
            disabledContainerColor = if (loading) p.accent else p.surfaceRaised,
            disabledContentColor = if (loading) p.onAccent else p.faint,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = p.onAccent, strokeWidth = 2.dp)
        } else {
            if (leadingGlyph != null) { Text(leadingGlyph); Spacer(Modifier.width(8.dp)) }
            Text(text)
            if (trailingGlyph != null) { Spacer(Modifier.width(8.dp)); Text(trailingGlyph) }
        }
    }
}

/** Secondary glass button: translucent fill, hairline border, primary text color. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
) {
    val p = LocalHisaabPalette.current
    val widthMod = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(widthMod).height(54.dp),
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

- [ ] **Step 2: Compile + full suite.** Run the build command. Expected: BUILD SUCCESSFUL, all tests pass (existing call sites unaffected — only optional params added).
- [ ] **Step 3: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/Buttons.kt && git commit -m "feat(design): PrimaryButton loading/glyph/width + GlassButton width"`

---

## Task 2: MidnightTextField primitive

**Files:** Create `composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightTextField.kt`

The missing field primitive: a `BasicTextField` in a Midnight surface row — Eyebrow label, optional mono lime prefix, faint placeholder, hairline border that animates to lime on focus, accent cursor, keyboardType/imeAction passthrough, and a `big` (62dp) hero variant. Serves Welcome (phone), Profile (name), RecoveryEntry (×24), and future settings/entry fields.

- [ ] **Step 1: Read `MoneyText.kt`** to learn how the repo obtains the bundled mono `FontFamily` (the prefix renders in mono). Reuse that exact mechanism for the prefix `fontFamily`. If MoneyText reads `HisaabTypography.families().mono` (or similar), call it the same way here.

- [ ] **Step 2: Write the primitive:**

```kotlin
package app.hisaab.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/** Midnight text field: surface row, hairline border → lime on focus, eyebrow label, optional
 *  mono lime [prefix], faint placeholder, accent cursor. [big] = 62dp hero variant. */
@Composable
fun MidnightTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    big: Boolean = false,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalHisaabPalette.current
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = when {
            isError -> p.negative
            focused -> p.accent
            else -> p.hair
        },
        label = "fieldBorder",
    )
    Column(modifier) {
        if (label != null) {
            Eyebrow(label)
            Spacer(Modifier.height(HisaabSpacing.sm))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (big) 62.dp else 54.dp)
                .clip(HisaabShapes.field)
                .background(p.surface, HisaabShapes.field)
                .border(1.5.dp, border, HisaabShapes.field)
                .padding(horizontal = HisaabSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) {
                // Mono lime prefix (e.g. "+880"); use the same mono FontFamily MoneyText uses.
                Text(prefix, color = p.accent)
                Spacer(Modifier.width(HisaabSpacing.sm))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, color = p.faint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = LocalTextStyle.current.copy(color = p.onBackground),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                )
            }
        }
    }
}
```

- [ ] **Step 3: Compile + full suite.** Run the build command → BUILD SUCCESSFUL / PASS. (If `KeyboardActions(onAny=...)` mismatches the CMP version, fall back to setting the matching per-action lambda.)
- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightTextField.kt && git commit -m "feat(design): MidnightTextField primitive"`

---

## Task 3: OtpCells primitive

**Files:** Create `composeApp/src/commonMain/kotlin/app/hisaab/design/components/OtpCells.kt`

Six segmented cells backed by a single hidden `BasicTextField` that owns focus. Renders the current value across cells with the next-to-fill cell lime-bordered. It is a pure pass-through: the CALLER keeps its own input filter (so the OtpScreen contract stays verbatim).

- [ ] **Step 1: Write the primitive:**

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/** Segmented OTP code field: [length] cells, active cell lime-bordered, mono digits. Pass-through:
 *  the caller applies its own digit/length filter in [onValueChange]. */
@Composable
fun OtpCells(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
) {
    val p = LocalHisaabPalette.current
    val focus = remember { FocusRequester() }
    Box(modifier.fillMaxWidth().clickable { focus.requestFocus() }) {
        // Hidden field owns focus + the system numeric keyboard.
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            modifier = Modifier.focusRequester(focus).size(1.dp).alpha(0f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm)) {
            repeat(length) { i ->
                val filled = i < value.length
                val active = i == value.length
                val cell = when {
                    isError -> p.negative
                    active -> p.accent
                    else -> p.hair
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(HisaabShapes.field)
                        .background(p.surface, HisaabShapes.field)
                        .border(1.5.dp, cell, HisaabShapes.field),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filled) value[i].toString() else "",
                        color = p.onBackground,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile + full suite.** Build command → BUILD SUCCESSFUL / PASS.
- [ ] **Step 3: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/OtpCells.kt && git commit -m "feat(design): OtpCells segmented code field"`

---

## Task 4: RecoveryWordGrid primitive (read-only chips)

**Files:** Create `composeApp/src/commonMain/kotlin/app/hisaab/design/components/RecoveryWordGrid.kt`

The shared 24-word read-only grid (2 columns), matching the Phase-7 settings reveal chip style: surface chip, hairline border, `field` radius, mono accent 1-based index + word in `onBackground`. Used by RecoveryPhraseScreen. (RecoveryEntry uses editable MidnightTextField rows instead — see Task 13 — to keep its LazyColumn lazy.)

- [ ] **Step 1: Write the primitive:**

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/** Read-only 2-column grid of the 24 recovery words. Mono accent index + word. Pass a
 *  weight/height modifier from the caller so it shares the column with the warning + CTA. */
@Composable
fun RecoveryWordGrid(words: List<String>, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
    ) {
        itemsIndexed(words) { index, word ->
            Row(
                Modifier
                    .clip(HisaabShapes.field)
                    .background(p.surface, HisaabShapes.field)
                    .border(1.dp, p.hair, HisaabShapes.field)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    color = p.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = HisaabSpacing.sm),
                )
                Text(text = word, color = p.onBackground, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

- [ ] **Step 2: Compile + full suite.** Build command → BUILD SUCCESSFUL / PASS.
- [ ] **Step 3: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/RecoveryWordGrid.kt && git commit -m "feat(design): RecoveryWordGrid read-only 24-word grid"`

---

## Task 5: SplashScreen

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/SplashScreen.kt`

- [ ] **Step 1: Read the file.** Confirm it is parameterless `fun SplashScreen()` reading only `palette`. Do NOT add routing/timers/params (routing lives in App.kt).
- [ ] **Step 2: Restyle.** Centered Box on `palette.background`. Render the হিসাব wordmark in the display face (`MaterialTheme.typography.displayMedium` or `displayLarge`), `palette.accent`, large. Replace the `CircularProgressIndicator` with three 7dp lime dots pulsing on staggered delays via a `rememberInfiniteTransition` (alpha 0.3→1.0, staggered phase per dot). Keep the wordmark string `"হিসাব"` byte-identical. Keep it cheap (the splash may unmount within a few frames) and visually correct if static.
- [ ] **Step 3: Compile + full suite.** Build command → BUILD SUCCESSFUL / PASS.
- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/SplashScreen.kt && git commit -m "feat(onboarding): Midnight splash (display wordmark + pulse dots)"`

---

## Task 6: WelcomeScreen

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/WelcomeScreen.kt`

- [ ] **Step 1: Read the file.** Keep signature `WelcomeScreen(onSendOtp, isLoading = false, error = null)` and the `Arrangement.SpaceBetween` top/bottom split + `weight(1f)` top block.
- [ ] **Step 2: Restyle.** Keep a small bespoke **lime** brand line "হিসাব · Hisaab" (do NOT use `Eyebrow` here — it uppercases + uses `faint`, which would mangle the Bengali glyph and drop the lime). Keep the `displaySmall` two-line headline "Your money,\nonly yours." and the muted subhead. Phone input → `MidnightTextField(value = phone, onValueChange = { phone = it }, label = "Phone number", placeholder = "1X XXXX XXXX", prefix = "+880", keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done, onImeAction = { if (phone.length >= 10) onSendOtp("+880$phone") }, big = true)`. CTA → `PrimaryButton(text = "Continue", onClick = { onSendOtp("+880$phone") }, enabled = phone.length >= 10 && !isLoading, loading = isLoading)`. Error line in `palette.negative` above the field.
- [ ] **Step 3: PRESERVE verbatim:** the `"+880$phone"` concatenation (both call sites), the `phone.length >= 10` gate (button `enabled` AND the IME guard), `error`/`isLoading` reads, and the bottom 24dp inset. No new nav lambda (welcome→otp is state-driven in the graph).
- [ ] **Step 4: Compile + full suite.** Build command → PASS. Confirm tour calls (`onSendOtp = {}`, `isLoading = true`, `error = "..."`) still typecheck.
- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/WelcomeScreen.kt && git commit -m "feat(onboarding): Midnight Welcome (MidnightTextField, PrimaryButton)"`

---

## Task 7: OtpScreen

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OtpScreen.kt`

- [ ] **Step 1: Read the file.** Keep signature `OtpScreen(phone, onVerify, onResend, isLoading = false, error = null)` and the `var otp` local state + SpaceBetween layout.
- [ ] **Step 2: Restyle.** `Eyebrow("Verify")`, `SectionHeader`-style headline "Enter the code\nwe sent you", muted "Sent to $phone" (phone may be accent). Code field → `OtpCells(value = otp, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otp = it }, isError = error != null)`. Verify CTA → `PrimaryButton(text = "Verify", onClick = { onVerify(otp) }, enabled = otp.length == 6 && !isLoading, loading = isLoading)`. Resend → a muted centered text affordance or `GlassButton("Resend code", onClick = onResend, fillMaxWidth = false)`. Error in `palette.negative`.
- [ ] **Step 3: PRESERVE verbatim:** the input filter `if (it.length <= 6 && it.all(Char::isDigit)) otp = it` (now passed into OtpCells), the gate `otp.length == 6 && !isLoading`, `onVerify(otp)`, `onResend()`, and the masked `NumberPassword` keyboard (OtpCells uses it). No back affordance, no nav.
- [ ] **Step 4: Compile + full suite.** Build command → PASS. Tour calls still typecheck.
- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OtpScreen.kt && git commit -m "feat(onboarding): Midnight OTP (OtpCells, PrimaryButton)"`

---

## Task 8: BiometricSetupScreen

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/BiometricSetupScreen.kt`

- [ ] **Step 1: Read the file.** Keep signature `BiometricSetupScreen(isAvailable, onEnroll, onSkip, isLoading = false, error = null)` exactly (ScreenshotTour renders available/unavailable frames).
- [ ] **Step 2: Restyle.** Centered hero: a 96dp `lime-soft` (`palette.accentSoft`) rounded-square tile (`RoundedCornerShape(28.dp)`) holding a large lime fingerprint/face text-glyph (e.g. a Unicode glyph, accent color). `SectionHeader(title = "Unlock with your\nface or finger", eyebrow = "Secure")`. Keep the security-promise body in `palette.muted` (re-style only; keep wording). Error in `palette.negative` when non-null. CTA → `PrimaryButton(text = if (isAvailable) "Enable biometric" else "Not available on this device", onClick = onEnroll, enabled = isAvailable && !isLoading, loading = isLoading)`. Skip → `GlassButton("Skip (not recommended)", onClick = onSkip)` (or a muted text affordance) with an 8–10dp gap.
- [ ] **Step 3: PRESERVE verbatim:** the conditional CTA label (both string literals — the tour asserts the two frames differ), the `enabled = isAvailable && !isLoading` gate, `onEnroll`/`onSkip` callbacks, the body copy "Your biometric unlocks Hisaab. Your data never leaves this device.", and no back/Scaffold. Keep `isAvailable`/`isLoading`/`error` as pure params (no local state).
- [ ] **Step 4: Compile + full suite.** Build command → PASS.
- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/BiometricSetupScreen.kt && git commit -m "feat(onboarding): Midnight biometric setup (lime tile, PrimaryButton/GlassButton)"`

---

## Task 9: RecoveryPhraseScreen (onboarding)

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/RecoveryPhraseScreen.kt`

- [ ] **Step 1: Read the file.** Keep signature `RecoveryPhraseScreen(words, onAcknowledged)` and the `var acknowledged` local state. The screen is a pure pass-through of `words` — do NOT copy/log/cache it.
- [ ] **Step 2: Restyle.** `SectionHeader(title = "Write these 24 words down", eyebrow = "Recovery")`. Warning → a `SurfaceCard` with "⚠ Lose these = lose your data" in `palette.negative` (mirror the Phase-7 settings reveal warning card). Word grid → `RecoveryWordGrid(words = words, modifier = Modifier.weight(1f))`. Ack row → a `SurfaceCard` containing `HCheck(checked = acknowledged, onCheckedChange = { acknowledged = it })` + the label "I've written down all 24 words in a safe place". CTA → `PrimaryButton(text = "I've saved them", onClick = onAcknowledged, enabled = acknowledged)`. Legacy `palette.rule`/`shapes.small` usages are replaced by `palette.hair`/`HisaabShapes.field` (handled inside RecoveryWordGrid).
- [ ] **Step 3: PRESERVE verbatim:** `enabled = acknowledged`, `onAcknowledged` fired once per press, all visible strings ("Recovery", "Write these 24 words down", "⚠ Lose these = lose your data", "I've written down all 24 words in a safe place", "I've saved them"), the `weight(1f)` on the grid, and no Scaffold/back.
- [ ] **Step 4: Compile + full suite.** Build command → PASS. Tour `RecoveryPhraseScreen(words = recoveryWords, onAcknowledged = {})` typechecks.
- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/RecoveryPhraseScreen.kt && git commit -m "feat(onboarding): Midnight recovery phrase (RecoveryWordGrid, HCheck, warning card)"`

---

## Task 10: ProfileSetupScreen

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/ProfileSetupScreen.kt`

- [ ] **Step 1: Read the file.** Keep signature `ProfileSetupScreen(onComplete, isLoading = false)` and the `name`/`locale` local state (init `""`/`"en"`).
- [ ] **Step 2: Restyle.** `SectionHeader(title = "What should we call you?", eyebrow = "Almost done")`. Name → `MidnightTextField(value = name, onValueChange = { name = it }, label = "Your name", placeholder = "Name", imeAction = ImeAction.Next, big = true)`. Language → an inline 2-up segmented control (screen-local, not a new primitive): a `Row(spacedBy(12.dp))` of two equal-`weight(1f)` clickable pill `Box`es — selected = `palette.accent` fill + `palette.onAccent` label, unselected = `palette.glass` fill + `1.dp palette.hair` border + `palette.onBackground` label, `HisaabShapes.pill`; "English" sets `locale = "en"`, "বাংলা" sets `locale = "bn"`. CTA → `PrimaryButton(text = "Start Hisaab", trailingGlyph = "→", onClick = { onComplete(name, locale) }, enabled = name.isNotBlank() && !isLoading, loading = isLoading)`.
- [ ] **Step 3: PRESERVE verbatim:** `onComplete(name, locale)` arg ORDER (name first), the literal locale values `"en"`/`"bn"` (emit values, not labels), the gate `name.isNotBlank() && !isLoading`, and no Scaffold/back/ViewModel ref.
- [ ] **Step 4: Compile + full suite.** Build command → PASS. Tour `ProfileSetupScreen(onComplete = { _, _ -> })` typechecks.
- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/ProfileSetupScreen.kt && git commit -m "feat(onboarding): Midnight profile setup (MidnightTextField, segmented locale, PrimaryButton)"`

---

## Task 11: CaptureOptInCard

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/CaptureOptInCard.kt`

- [ ] **Step 1: Read the file.** Keep signature `CaptureOptInCard(onTurnOn, onMaybeLater)` exactly (tour renders it).
- [ ] **Step 2: Restyle.** A centered `SurfaceCard`: a 44dp `palette.accentSoft` rounded tile (`RoundedCornerShape(13.dp)`) with a lime SMS text-glyph; a title (~18sp/700, `onBackground`); a privacy body in `palette.muted`; then a two-up button `Row(spacedBy(12.dp))`: `PrimaryButton("Turn on", onClick = onTurnOn, modifier = Modifier.weight(1f), fillMaxWidth = false)` beside `GlassButton("Maybe later", onClick = onMaybeLater, modifier = Modifier.weight(1f), fillMaxWidth = false)`. Preserve all visible copy.
- [ ] **Step 3: Compile + full suite.** Build command → PASS.
- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/CaptureOptInCard.kt && git commit -m "feat(onboarding): Midnight capture opt-in card (SurfaceCard, two-up CTAs)"`

---

## Task 12: LockScreen

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/LockScreen.kt`

- [ ] **Step 1: Read the file.** Keep signature `LockScreen(onUnlock)`. Locate the `openAndContinue()`/`attemptUnlock()` funcs, the `LaunchedEffect(Unit) { attemptUnlock() }`, and the `error`/`isLoading` local state. **DO NOT** touch any line inside the coroutine flow.
- [ ] **Step 2: Restyle ONLY the visual layer.** Centered Column on `palette.background`: an 84dp `palette.surface` tile (`RoundedCornerShape(26.dp)`, `1.dp palette.hair` border) holding a lime lock text-glyph (optional `GradientAvatar` instead); `Eyebrow("Locked")` + a display-face headline "Hisaab is locked" (`palette.onBackground`); error line in `palette.negative` when non-null; CTA → `PrimaryButton(text = "Unlock with biometric", leadingGlyph = "☝", onClick = { /* call the EXISTING attemptUnlock() */ }, enabled = !isLoading, loading = isLoading)` constrained to ~260dp width if desired (`fillMaxWidth = false` + width modifier).
- [ ] **Step 3: PRESERVE byte-identical:** `container.secureStorage.loadMasterSecret()`, the null-secret early return + message "Unable to load encryption key. Sign in again.", `container.openDatabase(secret)`, `secret.fill(0)`, `loadString("biometric_enabled") == "true"` gate + the no-biometric direct-open branch, `biometricAuth.authenticate(title = "Unlock Hisaab", subtitle = "Confirm your identity to continue")`, the exhaustive `when(BiometricResult)`, `UserCancelled` leaving no error, the open→zero→`onUnlock()` order, and `LaunchedEffect(Unit) { attemptUnlock() }` (key stays `Unit`, top-level). Keep strings "Hisaab is locked"/"Unlock with biometric".
- [ ] **Step 4: Compile + full suite.** Build command → PASS.
- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/LockScreen.kt && git commit -m "feat(lock): Midnight lock gate (display headline, PrimaryButton) — crypto flow untouched"`

---

## Task 13: RecoveryEntryScreen (restore)

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/recovery/RecoveryEntryScreen.kt`

- [ ] **Step 1: Read the file.** Keep signature `RecoveryEntryScreen(onRecovered)`, the `words = remember { mutableStateListOf(...) }` (24 empty), `error`/`isLoading`/`scope`, the `attemptRestore()` func, and `BIP39_WORDLIST`.
- [ ] **Step 2: Restyle.** `SectionHeader(title = "Enter your 24-word recovery phrase", eyebrow = "Recover")` + muted subtitle. Keep the `LazyColumn(weight(1f), spacedBy(8.dp))` (DO NOT hoist to a plain Column — laziness matters for 24 fields). Each item → `MidnightTextField(value = words[index], onValueChange = { words[index] = it }, label = "${index + 1}", singleLine = true, imeAction = ImeAction.Next)`. CTA → `PrimaryButton(text = "Restore", onClick = { attemptRestore() }, enabled = !isLoading, loading = isLoading)`. Error in `palette.negative`.
- [ ] **Step 3: PRESERVE byte-identical:** the entire crypto chain in order — `container.mnemonicService.decode(list)` (list = `words.toList().map { it.trim().lowercase() }`), `container.secureStorage.storeMasterSecret(masterSecret)`, `container.secureStorage.storeString("biometric_enabled", "false")`, `container.openDatabase(masterSecret)`, `masterSecret.fill(0)` (AFTER openDatabase), `onRecovered()` once. Keep BIP39 validation: blank-check → "Please fill in all 24 words"; membership `it !in BIP39_WORDLIST` → "Not in BIP39 wordlist: …"; try/catch → "Invalid recovery phrase: …". Keep `isLoading` guard/`!isLoading` enable. No back/nav.
- [ ] **Step 4: Compile + full suite.** Build command → PASS.
- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/recovery/RecoveryEntryScreen.kt && git commit -m "feat(recovery): Midnight restore-from-phrase (MidnightTextField rows) — crypto chain untouched"`

---

## Task 14: Gallery demos + verification

**Files:** Modify `composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt`

- [ ] **Step 1: Add demos** for the new/extended primitives, matching the file's existing `Eyebrow("…")` + inline-content section idiom: (a) `MidnightTextField` (stateful `var demoName`, with a `label`, `placeholder`, and a `prefix = "+880"` variant); (b) `OtpCells` (stateful `var code`, with the digit filter); (c) `RecoveryWordGrid` with a short sample word list (use a fixed-height `Modifier.height(...)` since the gallery scrolls — do NOT pass `weight` here); (d) `PrimaryButton(text = "Loading", onClick = {}, loading = true)` and `PrimaryButton(text = "Start", trailingGlyph = "→", onClick = {})`. Add the needed imports.
- [ ] **Step 2: Compile + full suite.** Build command → BUILD SUCCESSFUL / PASS.
- [ ] **Step 3: Verify the ScreenshotTour still compiles.** `export JAVA_HOME=/Users/xack/.sdkman/candidates/java/current && ./gradlew :composeApp:compileDebugAndroidTestSources --console=plain 2>&1 | tail -15` → BUILD SUCCESSFUL (confirms the six frozen signatures still satisfy ScreenshotTourTest).
- [ ] **Step 4: Controller best-effort emulator check.** Temporarily mount `ComponentGallery()` in MainActivity (or drive the onboarding flow), build+install on `emulator-5554`, wake (`adb -s emulator-5554 shell svc power stayon true; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard`; confirm `mWakefulness=Awake`), screenshot the new fields/cells/grid (mid-screen swipes), then `git checkout -- MainActivity.kt`. Compile + tests are the gate if the emulator is flaky.
- [ ] **Step 5: Commit.** `git add composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt && git commit -m "feat(onboarding): gallery demos for MidnightTextField/OtpCells/RecoveryWordGrid + PrimaryButton variants"`

---

## Done criteria for Phase 8

- Splash, Welcome, OTP, Biometric, RecoveryPhrase, Profile, CaptureOptInCard, Lock, and RecoveryEntry all render in Midnight (ink canvas, lime accent, Space Grotesk headlines, lime pill CTAs) in BOTH Dark and Light.
- New primitives `MidnightTextField`, `OtpCells`, `RecoveryWordGrid` exist and are demoed in the gallery; `PrimaryButton`/`GlassButton` gained `loading`/glyph/`fillMaxWidth` without breaking any call site.
- Raw `OutlinedTextField`/`Material Button`/`Checkbox`/`CircularProgressIndicator`-in-CTA migrated to Midnight primitives across these screens.
- `OnboardingViewModel` + `OnboardingGraph` untouched; every VM/nav/crypto/biometric call, gate, and user-visible string preserved verbatim; all six tour signatures unchanged.
- Full `commonTest` suite green; `compileDebugAndroidTestSources` green (ScreenshotTourTest compiles).

**Next phase:** `2026-05-31-midnight-phase-9-polish.md` — reduced-motion gating (splash pulse, biometric glow, field focus), both-theme contrast pass (ContrastTest cases for new fields/cells), screenshot-harness regen, `AccountsScreen` rename-dialog → MidnightDialog content-slot, gutter token consolidation (22dp→`HisaabSpacing.gutter`), and parked-Editorial-palette removal.
