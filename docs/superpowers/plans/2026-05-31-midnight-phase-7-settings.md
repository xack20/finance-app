# Midnight Redesign — Phase 7: Settings Suite — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the full Settings cluster to Midnight — hub, Accounts, Categories, Budgets, Auto-capture, Cloud-consent, Recovery-reveal — migrating raw `ModalBottomSheet`/`AlertDialog`/`Switch`/`RadioButton`/`Slider` onto the existing Midnight primitives (+ one new `MidnightSlider`), changing **no ViewModel/repository/data**.

**Architecture:** Pure presentation restyle reusing `SurfaceCard`, `MoneyText`, `Eyebrow`, `SectionHeader`, `MidnightSheet`, `MidnightDialog`, `HToggle`/`HCheck`/`HRadio`, `GlyphChip`, `PrimaryButton`/`GlassButton`. One new component `MidnightSlider` (lime-recolored Material3 `Slider`) for the auto-post confidence threshold. Several Settings screens have NO ViewModel — they read `container.*Repository.observe*()` and call repo methods directly; **keep all those repo/VM calls verbatim**.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.10.1.

**Spec:** `docs/superpowers/specs/2026-05-31-midnight-redesign-design.md` (§7, §8). **Design source:** `docs/design/design_handoff_hisaab_midnight/README.md` (§Settings suite).

**Scope:** The 7 settings screens + shared `SettingsComponents.kt`. Do NOT touch any ViewModel (`SettingsViewModel`, `AutoCaptureViewModel`), repository, `container.*`, or domain. Reduced-motion deferred to Phase 9.

---

## Task 1: MidnightSlider (new component)

**Files:** Create `composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightSlider.kt`

- [ ] **Step 1: Implement** (lime-recolored Material3 Slider — reliable; the spec's custom draggable is not worth the risk).

```kotlin
package app.hisaab.design.components

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hisaab.design.LocalHisaabPalette

/** Midnight slider: lime active track + thumb, surfaceRaised inactive track. */
@Composable
fun MidnightSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val p = LocalHisaabPalette.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = p.accent,
            activeTrackColor = p.accent,
            inactiveTrackColor = p.surfaceRaised,
            activeTickColor = p.onAccent,
            inactiveTickColor = p.hair,
        ),
    )
}
```

- [ ] **Step 2: Compile.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL. (Fall back to `/usr/libexec/java_home`.)

- [ ] **Step 3: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightSlider.kt && git commit -m "feat(design): MidnightSlider (lime-recolored Material3 slider)"`

---

## Task 2: Settings hub (SettingsScreen.kt + SettingsComponents.kt)

**Files:** Modify `screens/settings/SettingsScreen.kt` and `screens/settings/SettingsComponents.kt`

- [ ] **Step 1: Read both in full.** Keep `SettingsViewModel` wiring (`setLockTimeoutMs`, `setBiometricEnabled`, `signOut(onDone)`) and the `onRecoveryReveal`/`onAccounts`/`onCategories`/`onBudgets`/`onAutoCapture` nav lambdas verbatim.

- [ ] **Step 2: Restyle (UI only):**
  - Title `SectionHeader("Settings")`.
  - Wrap each group (Privacy / Data / Capture / About) in a `SurfaceCard`; precede each with an `Eyebrow`. The shared `SettingRow` (in `SettingsComponents.kt`) stays the row primitive — restyle its chrome to Midnight tokens (label `onBackground`, value `muted` or `accent` for "Reveal", trailing "›" `muted`, divider `hair`).
  - Biometric toggle: replace the `Switch` in `SettingToggleRow` with `HToggle(checked, onCheckedChange = { viewModel.setBiometricEnabled(it) })`.
  - **Lock-timeout sheet:** replace the `ModalBottomSheet` with `MidnightSheet(onDismiss = { showLockSheet = false }, title = "Lock timeout")` — 4 option rows (Immediate/30 seconds/5 minutes/Never), each a clickable row with a trailing "✓" (`accent`) when selected, calling `viewModel.setLockTimeoutMs(...)`. Keep `formatLockTimeout` for the row value display.
  - **Sign-out dialog:** replace the `AlertDialog` with `MidnightDialog(onDismiss = …, title = "Sign out of Hisaab?", body = "…", confirmLabel = "Sign out", onConfirm = { viewModel.signOut(onDone) }, destructive = true)`.
  - "Sign out" entry: centered `negative` text button.

- [ ] **Step 3: Compile + full unit suite.** `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS. (Note: `SettingsComponents.SettingRow` is also used by `AutoCaptureScreen` — confirm that screen still compiles.)

- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/SettingsScreen.kt composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/SettingsComponents.kt && git commit -m "feat(settings): Midnight Settings hub (cards, HToggle, MidnightSheet/Dialog)"`

---

## Task 3: AccountsScreen.kt

**Files:** Modify `screens/settings/AccountsScreen.kt`

- [ ] **Step 1: Read it in full.** No VM — keep `container.accountRepository.observeActive()` + the direct `archive`/`add`/`rename` repo calls verbatim.

- [ ] **Step 2: Restyle (UI only):**
  - Header `SectionHeader("Accounts")` + "+ Add" (`accent`) → opens add sheet.
  - Each account = a `SurfaceCard`: name (`onBackground`/600) + `Eyebrow("${kind} · ${currency}")` + trailing balance via `MoneyText`. CARD extras (`CardSummaryRow`/`CardSummaryChip`): use `MoneyText` for Outstanding (negative tone)/Available (positive)/Due, each with an `Eyebrow` label.
  - **AddAccountSheet:** `ModalBottomSheet` → `MidnightSheet(title = "New account")`: name `OutlinedTextField`; "Kind" `Eyebrow` + pill chips (the existing kind `Button` row → restyle to pill chips, selected = `accent`/`onAccent`, else `hair` border/`muted`); CARD reveals credit-limit (৳ prefix)/statement-day/due-day fields; `PrimaryButton("Add account")` (disabled until name). Keep all repo `add(...)` wiring.
  - **RenameAccountDialog:** `AlertDialog` → keep an `AlertDialog` styled Midnight (it has a text-field body, so don't collapse to `MidnightDialog`): `containerColor = palette.surface`, `shape = HisaabShapes.card`, accent confirm.

- [ ] **Step 3: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AccountsScreen.kt && git commit -m "feat(settings): Midnight Accounts (cards, MoneyText card stats, MidnightSheet)"`

---

## Task 4: CategoriesScreen.kt + BudgetsScreen.kt

**Files:** Modify `screens/settings/CategoriesScreen.kt` and `screens/settings/BudgetsScreen.kt`

- [ ] **Step 1: Read both.** No VMs — keep `categoryRepository`/`budgetRepository` direct calls (`add`/`archive`/`set`) verbatim.

- [ ] **Step 2: `CategoriesScreen`:** header `SectionHeader("Categories")` + "+ Add". Rows in a `SurfaceCard`: `GlyphChip` for the category icon/emoji (render the emoji as a Text inside a hue tile, or the inline-vector glyph + `categoryHue`) + name + a "Default" `Eyebrow` badge if default; divider `hair`. **AddCategorySheet** → `MidnightSheet(title = "New category")`: name field + icon picker + `PrimaryButton("Add")`.

- [ ] **Step 3: `BudgetsScreen`:** header `SectionHeader("Budgets")` + "+ Add". Rows in `SurfaceCard`: categoryName + `"৳${cap.toInt()}/mo · since ${ym}"` (muted) + Archive (`negative` text). Empty state stays. **AddBudgetSheet** → `MidnightSheet(title = "New budget")`: category list rows using `HRadio(selected, onClick)` (replace Material `RadioButton`) + monthly-cap `OutlinedTextField` (৳ prefix) + `PrimaryButton("Save")` (disabled until pick && cap). Keep `set(...)` wiring.

- [ ] **Step 4: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/CategoriesScreen.kt composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/BudgetsScreen.kt && git commit -m "feat(settings): Midnight Categories + Budgets (glyph chips, HRadio, MidnightSheet)"`

---

## Task 5: AutoCaptureScreen.kt (densest)

**Files:** Modify `screens/settings/AutoCaptureScreen.kt`

- [ ] **Step 1: Read it in full.** Keep `AutoCaptureViewModel` + EVERY event verbatim: `setCaptureEnabled`, `setEngineMode`, `setCloudProvider`, `apiKeyFor`/`setApiKey`, `validateApiKey`, `setRedaction`, `setAlwaysReview`, `setAutoPostThreshold`/`resetThresholdToDefault`, `setSenderEnabled`/`setSenderAccount`, `addSender`, `backfillLast90Days`. Keep the `onConsent` nav lambda and the `smsSupported` iOS early-return.

- [ ] **Step 2: Restyle (UI only), section by section:**
  - Master SMS toggle row: `SurfaceCard` + `HToggle(config.captureEnabled, onCheckedChange = { viewModel.setCaptureEnabled(it) })`.
  - Permission-denied warning: a `SurfaceCard` bordered `negative` with a warn glyph + "SMS permission denied" + a lime "Grant" pill (re-toggles).
  - Engine cards (`EnginePicker`): a `SurfaceCard` with two selectable rows (on-device/cloud) — each a row + trailing "✓" (`accent`) when active, calling `setEngineMode(...)`.
  - Cloud provider radios: `HRadio` rows (Claude/OpenAI/Gemini) → `setCloudProvider(...)`.
  - API key (`ApiKeyField`) + Validate row: keep the field (password), restyle the Validate button (`GlassButton`) + the checking/valid(`positive`)/invalid(`negative`) status text.
  - Model row + Cloud-consent row: `SettingRow`-style; consent value "Review" `accent` → `onConsent`.
  - Redact-PII toggle → `HToggle` (`setRedaction`).
  - Trust: always-review `HToggle` (`setAlwaysReview`); when off → "Auto-post confidence" `muted` + `{conf}%` `accent` mono + `MidnightSlider(value = threshold, onValueChange = { setAutoPostThreshold(it.toDouble()) }, valueRange = 0.5f..0.99f)` + a reset → `resetThresholdToDefault()`.
  - Senders: new-sender prompt `SurfaceCard`; per-sender `HToggle` rows (`setSenderEnabled`); add-sender inline form (`setSenderAccount`/`addSender`).
  - Import 90 days: `GlassButton` (disabled unless master on) → `backfillLast90Days()`.
  - iOS explainer (`IosUnavailableExplainer`): a single `SurfaceCard`.

- [ ] **Step 3: Compile + full unit suite.** `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS.

- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AutoCaptureScreen.kt && git commit -m "feat(settings): Midnight Auto-capture (HToggle/HRadio, MidnightSlider, cards)"`

---

## Task 6: CloudConsentScreen.kt + RecoveryPhraseRevealScreen.kt

**Files:** Modify `screens/settings/CloudConsentScreen.kt` and `screens/settings/RecoveryPhraseRevealScreen.kt`

- [ ] **Step 1: Read both.** Keep wiring: CloudConsent's `onGrant`/`onRevoke`/`onBack` lambdas; RecoveryReveal's `container.biometricAuth/secureStorage/mnemonicService` `LaunchedEffect` + `onBack` verbatim (do NOT change the crypto/biometric flow or `secret.fill(0)`).

- [ ] **Step 2: `CloudConsentScreen`:** consent copy in `onBackground`/`muted`; granted → `positive` "Consent granted." + `GlassButton("Revoke consent")` (negative text) → `onRevoke`; ungranted → `PrimaryButton("I agree — use cloud parsing")` → `onGrant`+`onBack`.

- [ ] **Step 3: `RecoveryPhraseRevealScreen`:** inline `negative` warning card; error variant `Eyebrow`/`negative`; the 24-word `LazyVerticalGrid` cells → `SurfaceCard`/hairline style: each cell = mono `accent` index + word (`onBackground`). Keep loading `CircularProgressIndicator` (or restyle to a lime spinner). Do NOT change the biometric gating or `secret.fill(0)`.

- [ ] **Step 4: Compile + full unit suite.** `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS.

- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/CloudConsentScreen.kt composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/RecoveryPhraseRevealScreen.kt && git commit -m "feat(settings): Midnight Cloud-consent + Recovery-reveal"`

---

## Task 7: Gallery slider demo + verification

**Files:** Modify `composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt`

- [ ] **Step 1: Add a `MidnightSlider` demo** (stateful `var conf by remember { mutableStateOf(0.85f) }`, range 0.5f..0.99f) + the import. Append inside the gallery Column.

- [ ] **Step 2: Compile + full suite.** `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS.

- [ ] **Step 3: Controller visual check (best-effort).** Controller temporarily mounts `ComponentGallery()`, wakes the emulator (`adb shell svc power stayon true; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; swipe up from MID-screen`; confirm `mWakefulness=Awake` + keyguard gone), builds, installs, scrolls (mid-screen swipes — bottom-edge triggers the home gesture), screenshots the slider, then reverts MainActivity. (See saved emulator gotchas; compile + tests are the gate if flaky.)

- [ ] **Step 4: Commit.** `git add composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt && git commit -m "feat(settings): gallery MidnightSlider demo"`

---

## Done criteria for Phase 7

- All 7 settings screens compile and render in Midnight: hub cards, accounts/categories/budgets cards + MidnightSheets, auto-capture (HToggle/HRadio/MidnightSlider), cloud-consent, recovery grid.
- Raw `ModalBottomSheet`/`AlertDialog`/`Switch`/`RadioButton`/`Slider` migrated to Midnight primitives.
- Full `commonTest` suite green; no ViewModel/repo/data change.
- `MidnightSlider` renders in the gallery.

**Next phase:** `2026-05-31-midnight-phase-8-onboarding-lock.md` — onboarding flow (splash, welcome, OTP, biometric, recovery, profile, capture opt-in) + lock/restore, completing the screen ports. Then Phase 9 polish (reduced-motion, both-theme contrast pass, screenshot-harness regen).
