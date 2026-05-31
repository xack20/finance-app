# Midnight Redesign — Phase 6: Assistant + People — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the Assistant (agent chat) and People (list + detail) screens to Midnight — glowing sparkle orb, lime user bubbles, typing dots, listening equalizer, suggestion chips, glass input bar (Assistant); gradient avatars, balance cards, lend/borrow history, settle sheet (People) — changing **no ViewModel or agent/data logic**.

**Architecture:** New shared `GradientAvatar` (design/components) + new Assistant animation atoms (in `screens/agent/`). Screens compose Phase 2/3 primitives (`SurfaceCard`, `MoneyText`, `Eyebrow`, `MidnightSheet`, `MidnightDialog`, `HRadio`, `PrimaryButton`/`GlassButton`). The project has **no material-icons** — use Unicode glyphs (✦ sparkle, ↑ send) and the inline-`ImageVector` pattern where a vector is needed.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.10.1 (animation: `rememberInfiniteTransition`).

**Spec:** `docs/superpowers/specs/2026-05-31-midnight-redesign-design.md` (§6, §8). **Design source:** `docs/design/design_handoff_hisaab_midnight/README.md` (§Assistant, §Review inbox, §People).

**Scope:** Assistant + People screens + their components. Do NOT touch `AgentViewModel`, `PeopleViewModel`, `app/hisaab/agent/`, `llm/`, repos, or domain. Reduced-motion gating of the looping animations is deferred to Phase 9 polish (keep animations gentle).

---

## Task 1: GradientAvatar (shared component)

**Files:** Create `composeApp/src/commonMain/kotlin/app/hisaab/design/components/GradientAvatar.kt`

- [ ] **Step 1: Implement.**

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors

/** Circular avatar with violet→blue gradient + up-to-2-letter initials from [name]. */
@Composable
fun GradientAvatar(name: String, modifier: Modifier = Modifier, size: Int = 44) {
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(HisaabColors.categoryHues.getValue("violet"), HisaabColors.categoryHues.getValue("blue")),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.34f).sp)
    }
}
```

- [ ] **Step 2: Compile.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL. (Fall back to `/usr/libexec/java_home`.)

- [ ] **Step 3: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/GradientAvatar.kt && git commit -m "feat(design): GradientAvatar (violet to blue initials)"`

---

## Task 2: Assistant animation atoms

**Files:** Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/AssistantAtoms.kt`

- [ ] **Step 1: Implement** the sparkle orb, typing dots, listening equalizer, and suggestion-chip row.

```kotlin
package app.hisaab.screens.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.LocalHisaabPalette

/** Glowing lime sparkle tile (assistant avatar / empty-state hero). */
@Composable
fun SparkleOrb(modifier: Modifier = Modifier, size: Int = 38) {
    val p = LocalHisaabPalette.current
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(p.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text("✦", color = p.accent, fontSize = (size * 0.5f).sp) // ✦
    }
}

/** Three pulsing lime dots — the assistant "typing" indicator. */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val t = rememberInfiniteTransition(label = "typing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.3f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 180), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(7.dp).clip(CircleShape).background(p.accent.copy(alpha = a)))
        }
    }
}

/** Five pulsing lime bars — the mic "listening" equalizer. */
@Composable
fun ListeningEqualizer(modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val t = rememberInfiniteTransition(label = "eq")
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            val h by t.animateFloat(
                6f, 6f + (i % 3) * 6f + 8f,
                infiniteRepeatable(tween(700, delayMillis = i * 100), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
        }
    }
}

/** Horizontal scrollable suggestion chips; tap → [onPick]. Purely presentational. */
@Composable
fun SuggestionChips(suggestions: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { s ->
            Text(
                s,
                color = p.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(p.glass)
                    .border(1.dp, p.hair, RoundedCornerShape(999.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/AssistantAtoms.kt && git commit -m "feat(agent): Midnight assistant atoms (orb, typing dots, equalizer, suggestion chips)"`

---

## Task 3: AgentScreen restyle

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/AgentScreen.kt`

- [ ] **Step 1: Read `AgentScreen.kt` in full.** Keep `AgentScreenContent`'s state params + every event callback (`onInputChange`, `onSend`, `onMicTap`, `onConsent`, `onToggleInclude`, `onEditWrite`, `onApply`, `onNewChat`, `onDismissBanner`) and all `testTag`s verbatim (tests depend on `agent_input`, `agent_send`, `agent_mic`, `agent_inflight`, `agent_gate_banner`, `agent_error`).

- [ ] **Step 2: Restyle (UI only):**
  - **Header:** back/close + `SparkleOrb()` + a title column ("Assistant" `onBackground`/600 + "On-device · private" `muted` 12sp) + a "New" text button in `accent` → `onNewChat`.
  - **Bubbles:** user = `palette.accent` bg + `palette.onAccent` text, right-aligned, shape `RoundedCornerShape(20.dp,20.dp,6.dp,20.dp)`; assistant = `palette.surface` bg + 1dp `palette.hair` + `palette.onBackground` text, left-aligned, shape `RoundedCornerShape(20.dp,20.dp,20.dp,6.dp)`; max width ~82%, padding 13×16dp.
  - **Typing indicator:** replace the `CircularProgressIndicator` (keep its `testTag("agent_inflight")`) with a surface bubble containing `TypingDots()`.
  - **Empty state:** when `messages.isEmpty()` and not gated, show centered `SparkleOrb(size=64)` + "Ask about your money" (title) + a muted line + `SuggestionChips(listOf("Set a food budget","I paid 500 for lunch","Move 1000 to cash")) { onInputChange(it); onSend() }`.
  - **Input bar:** glass row — text field (`agent_input` tag kept) h50 with `RoundedCornerShape(999.dp)`, border `accent` when `listening` else `hair`; circular **send** 50dp (`agent_send` tag) bg `accent` when input non-blank else `surfaceRaised`, "↑" in `onAccent`/`faint`; circular **mic** 50dp (`agent_mic` tag) bg `accent` when `listening` else `surface`, a mic glyph, `onMicTap`. When `listening`, show `ListeningEqualizer()` + "Listening…" `accent` above the bar.
  - **Unavailable gate:** `SurfaceCard` (keep `testTag("agent_gate_banner")`) with the reason text; disable chips/input/mic.
  - **Banners:** error (`agent_error` tag) `negative`; confirmation `positive`.
  - Container `palette.background`.

- [ ] **Step 3: Compile + full unit suite** (instrumented agent tests rely on testTags — keep them): `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS.

- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/AgentScreen.kt && git commit -m "feat(agent): Midnight assistant chat (orb header, lime bubbles, glass input, empty state)"`

---

## Task 4: ReviewCard + AgentConsentDialog restyle

**Files:** Modify `screens/agent/ReviewCard.kt` and `screens/agent/AgentConsentDialog.kt`

- [ ] **Step 1: Read both.**

- [ ] **Step 2: `ReviewCard`** — wrap in `SurfaceCard`; "Review & apply" header via `Eyebrow`/title; per proposed write: `HCheck`(included toggle, wired to existing `onToggleInclude`) + `{label} · {detail}` + signed `MoneyText` (opacity reduced when not included); **Apply** → `PrimaryButton` (existing `onApply`, disabled unless any included). Keep all existing callbacks + any testTags.

- [ ] **Step 3: `AgentConsentDialog`** — restyle the `AlertDialog` to Midnight tokens (`containerColor = palette.surface`, `shape = HisaabShapes.card`, title `onBackground`, body `muted`, confirm `accent`, dismiss `muted`). Keep its rich two-list disclosure body and the existing confirm/dismiss callbacks (do NOT collapse to `MidnightDialog`, which only takes a `body` string).

- [ ] **Step 4: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/ReviewCard.kt composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/AgentConsentDialog.kt && git commit -m "feat(agent): Midnight review card + consent dialog"`

---

## Task 5: People list + person detail restyle

**Files:** Modify `screens/people/PeopleListScreen.kt` and `screens/people/PersonDetailScreen.kt`

- [ ] **Step 1: Read both in full.** Keep `PeopleViewModel` wiring + events (`settle`, `addManualPerson`, `upsertFromContact`) + nav lambdas verbatim.

- [ ] **Step 2: `PeopleListScreen`** — header `SectionHeader("People")` + "+ Add" in `accent`. Each person row = a clickable `SurfaceCard` row: `GradientAvatar(person.name, size=44)` + a column (name `onBackground`/600 + status "Owes you"/"You owe"/"Settled" `muted` 13sp by balance sign) + trailing `MoneyText(balance, signed = true)` (zero → "Settled" muted). Empty state stays. `AddPersonSheet` → `MidnightSheet(title="Add person")` with an `OutlinedTextField` + `PrimaryButton("Add")` (disabled if blank) + a "Or pick from contacts" text/`GlassButton` (keep `upsertFromContact`/contacts wiring).

- [ ] **Step 3: `PersonDetailScreen`** — top bar = name + Back. Balance card = `SurfaceCard` with `GradientAvatar(person.name, size=54)` + `Eyebrow("They owe you"/"You owe them"/"Settled")` + `MoneyText(balance, signed=true, style=displayLarge)`. "History" `Eyebrow`. Each `LendBorrowRowItem` = `SurfaceCard` row: a 38dp rounded icon tile (`positiveSoft` + lend glyph if LENT, else `negativeSoft` + borrow glyph; use a Unicode arrow ↑/↓) + a column ("Lent/Borrowed ${amount.toTaka()}" + "{note} · {status}" `muted`) + a **Settle** pill (`accentSoft` bg, `accent` text, `RoundedCornerShape(999.dp)`) when not SETTLED → opens settle. `SettleDialog` → a `MidnightSheet` (or styled `AlertDialog`): amount `OutlinedTextField` (৳ prefix) + `Eyebrow("Into account")` + account list using `HRadio` (replace Material `RadioButton`, wired to the existing selection) + Cancel/Settle buttons calling the existing `settle(...)`.

- [ ] **Step 4: Compile + full unit suite.** `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS.

- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/people/PeopleListScreen.kt composeApp/src/commonMain/kotlin/app/hisaab/screens/people/PersonDetailScreen.kt && git commit -m "feat(people): Midnight People list + detail (gradient avatars, balance cards, settle)"`

---

## Task 6: Gallery atoms demo + verification

**Files:** Modify `composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt`

- [ ] **Step 1: Add demos** for `GradientAvatar`, `SparkleOrb`, `TypingDots`, `ListeningEqualizer`, `SuggestionChips` (with `onPick = {}`). Add the imports. Append inside the gallery Column.

- [ ] **Step 2: Compile + full suite.** `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS.

- [ ] **Step 3: Controller visual check (best-effort).** Controller temporarily mounts `ComponentGallery()`, **wakes the emulator** (`adb shell svc power stayon true; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; swipe up from mid-screen`; confirm `mWakefulness=Awake` + keyguard gone), builds, installs, screenshots (avatar gradient, lime orb, typing dots, chips), then reverts MainActivity. (See the saved emulator gotchas; if the emulator stays flaky, compile + tests are the gate.)

- [ ] **Step 4: Commit.** `git add composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt && git commit -m "feat(agent): gallery demos for avatar + assistant atoms"`

---

## Done criteria for Phase 6

- Assistant + People compile and render in Midnight: orb header, lime bubbles, typing dots, empty state + suggestion chips, glass input bar + send/mic; gradient avatars, balance cards, lend/borrow history, settle sheet.
- All existing agent `testTag`s preserved; full `commonTest` suite green; no ViewModel/agent/data change.
- Gallery renders the avatar + assistant atoms.

**Next phase:** `2026-05-31-midnight-phase-7-settings.md` — Settings hub + accounts/categories/budgets/auto-capture/cloud-consent/recovery, migrating their sheets/dialogs onto `MidnightSheet`/`MidnightDialog`.
