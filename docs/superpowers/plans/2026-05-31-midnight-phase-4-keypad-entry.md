# Midnight Redesign — Phase 4: Keypad Entry — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Entry screen as the Midnight **tactile numeric keypad** — a big live amount colored by kind, Midnight kind-chips, and a 3×4 keypad pinned at the bottom — driving the existing `EntryViewModel` API **unchanged**.

**Architecture:** The amount is no longer a soft-keyboard text field; a custom `NumericKeypad` computes the new amount string via a pure, unit-tested `applyAmountKey(current, key)` helper and calls the existing `viewModel.setAmount(newString)` (full-replacement, as today). The screen becomes keypad-anchored: a scrollable detail section (reusing the existing `AccountPicker`/`CategoryPicker`/`NotesField`/`FieldRow`/sheets verbatim) with the keypad + a lime "Save entry" button pinned at the bottom. **No ViewModel, repository, or `EntryFormState` change.**

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.10.1, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-05-31-midnight-redesign-design.md` (§7). **Design source:** `docs/design/design_handoff_hisaab_midnight/README.md` (§Entry keypad).

**Scope:** The Entry screen only. The detail-field sheets (Account/Category/Person/Split pickers) keep their current behavior (a later polish phase migrates them onto `MidnightSheet`); this phase keeps them working and focuses on the keypad + amount + layout. Do NOT touch `EntryViewModel.kt`, `EntryFormState.kt`, `SplitEditorSheet.kt`, or any repository.

---

## Reference (amount color by kind, from the spec)
- amount empty or `"0"` → `faint`
- `INCOME` → `positive`
- `EXPENSE` → `onBackground`
- `TRANSFER` / `LEND` / `BORROW` → `accent`

The keypad's backspace key emits the char `'⌫'` (⌫).

---

## Task 1: `applyAmountKey` amount-entry logic (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/AmountKey.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/screens/entry/AmountKeyTest.kt`

- [ ] **Step 1: Write the failing tests (RED).** Create `AmountKeyTest.kt`:

```kotlin
package app.hisaab.screens.entry

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountKeyTest {
    @Test fun first_digit_replaces_empty() = assertEquals("5", applyAmountKey("", '5'))
    @Test fun digit_replaces_leading_zero() = assertEquals("5", applyAmountKey("0", '5'))
    @Test fun digits_append() = assertEquals("123", applyAmountKey("12", '3'))
    @Test fun dot_appends() = assertEquals("12.", applyAmountKey("12", '.'))
    @Test fun dot_on_empty_makes_zero_point() = assertEquals("0.", applyAmountKey("", '.'))
    @Test fun second_dot_ignored() = assertEquals("12.", applyAmountKey("12.", '.'))
    @Test fun max_two_decimals() {
        assertEquals("12.50", applyAmountKey("12.5", '0'))
        assertEquals("12.50", applyAmountKey("12.50", '5')) // 3rd decimal blocked
    }
    @Test fun backspace_drops_last() = assertEquals("12", applyAmountKey("123", '⌫'))
    @Test fun backspace_on_empty_stays_empty() = assertEquals("", applyAmountKey("", '⌫'))
    @Test fun integer_length_capped() = assertEquals("123456789012", applyAmountKey("123456789012", '3'))
}
```

- [ ] **Step 2: Run to verify it fails.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.screens.entry.AmountKeyTest"` → FAIL (unresolved `applyAmountKey`). (Fall back to `/usr/libexec/java_home`.)

- [ ] **Step 3: Implement.** Create `AmountKey.kt`:

```kotlin
package app.hisaab.screens.entry

/** Backspace key char emitted by the numeric keypad. */
const val BACKSPACE = '⌫' // ⌫

/**
 * Apply one keypad [key] to the current amount [current] and return the new amount string.
 * Rules: a digit replaces a sole leading "0"; only one '.'; max 2 decimals; integer part capped
 * at 12 digits; backspace drops the last char. Pure — no side effects.
 */
fun applyAmountKey(current: String, key: Char): String = when (key) {
    BACKSPACE -> current.dropLast(1)
    '.' -> when {
        current.contains('.') -> current
        current.isEmpty() -> "0."
        else -> "$current."
    }
    in '0'..'9' -> {
        val dot = current.indexOf('.')
        when {
            dot >= 0 && current.length - dot - 1 >= 2 -> current   // already 2 decimals
            dot < 0 && current.length >= 12 -> current             // integer cap
            current == "0" -> key.toString()                        // replace lone leading zero
            else -> current + key
        }
    }
    else -> current
}
```

- [ ] **Step 4: Run to verify it passes.** `... --tests "app.hisaab.screens.entry.AmountKeyTest"` → PASS (all 10).

- [ ] **Step 5: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/AmountKey.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/screens/entry/AmountKeyTest.kt
git commit -m "feat(entry): applyAmountKey keypad amount logic (TDD)"
```

---

## Task 2: Keypad UI pieces — NumericKeypad + BigAmount + KindChipRow

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryKeypad.kt`

- [ ] **Step 1: Implement.**

```kotlin
package app.hisaab.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.TxnKind
import app.hisaab.util.toTaka

/** Big live amount, ৳ prefix, grouped, colored by [kind]. Empty/zero shows a faint "0". */
@Composable
fun BigAmount(amount: String, kind: TxnKind, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val isZero = amount.isEmpty() || (amount.toDoubleOrNull()?.let { it == 0.0 } ?: false)
    val color = when {
        isZero -> p.faint
        kind == TxnKind.INCOME -> p.positive
        kind == TxnKind.EXPENSE -> p.onBackground
        else -> p.accent
    }
    // Render with grouped thousands but keep a trailing "." the user is mid-typing.
    val display = run {
        val n = amount.toDoubleOrNull()
        when {
            amount.isEmpty() -> "৳0"
            n == null -> "৳$amount"
            else -> {
                val dec = amount.substringAfter('.', "").length.coerceAtMost(2)
                val grouped = n.toTaka(decimals = dec)
                if (amount.endsWith(".")) "$grouped." else grouped
            }
        }
    }
    Text(
        display,
        modifier = modifier.fillMaxWidth(),
        color = color,
        fontSize = 56.sp,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.displayLarge,
    )
}

/** Midnight kind chips: selected = onBackground fill + background label; else transparent + hair. */
@Composable
fun KindChipRow(kind: TxnKind, onSelect: (TxnKind) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val kinds = listOf(
        TxnKind.EXPENSE to "Expense", TxnKind.INCOME to "Income",
        TxnKind.TRANSFER to "Transfer", TxnKind.LEND to "Lend", TxnKind.BORROW to "Borrow",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.forEach { (k, label) ->
            val on = k == kind
            Text(
                label,
                color = if (on) p.background else p.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) p.onBackground else Color.Transparent)
                    .border(1.dp, if (on) p.onBackground else p.hair, RoundedCornerShape(999.dp))
                    .clickable { onSelect(k) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** 3×4 numeric keypad: 1–9, '.', 0, ⌫. Emits each key char (backspace = [BACKSPACE]). */
@Composable
fun NumericKeypad(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('.', '0', BACKSPACE),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key -> KeypadKey(key, onKey, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: Char, onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Text(
        if (key == BACKSPACE) "⌫" else key.toString(),
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.surface)
            .clickable { onKey(key) }
            .padding(vertical = 14.dp),
        color = if (key == BACKSPACE) p.muted else p.onBackground,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}
```

> Note: `KeypadKey` is a normal private composable; `Modifier.weight(1f)` is built at its call site inside the `Row` lambda (RowScope), so it resolves there and `KeypadKey` just receives the finished `Modifier`.

- [ ] **Step 2: Compile.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL. Resolve any import/resolution issue minimally and report.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryKeypad.kt
git commit -m "feat(entry): NumericKeypad + BigAmount + KindChipRow keypad UI"
```

---

## Task 3: Rebuild EntryScreen as keypad-anchored

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryScreen.kt`

Rebuild ONLY the main `EntryScreen` composable's layout (currently lines ~54–243). **Keep unchanged:** the ViewModel/state setup (lines 57–79), the bottom sheets block (`SplitEditorSheet` + `PersonPickerSheet`, lines 226–242), and ALL private helpers + format functions (`FieldRow`, `TagChipInput`, `AttachmentRow`, `PersonPickerSheet`, `formatDateTime`, `formatDate`, lines 245–375).

- [ ] **Step 1: Read the current `EntryScreen.kt` in full** so you keep the VM setup, sheets, and private helpers verbatim.

- [ ] **Step 2: Replace the `Scaffold(...) { padding -> Column(...) { ... } }` body** (the topBar + the scrolling Column, roughly lines 81–224) with the keypad-anchored layout below. The amount soft-keyboard `AmountField(...)` is removed; `BigAmount` + `NumericKeypad` replace it; `KindSelector(...)` is replaced by `KindChipRow(...)`; Save moves to a bottom `PrimaryButton`.

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New entry", color = palette.onBackground) },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Cancel", color = palette.muted) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Scrollable detail section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                KindChipRow(kind = state.kind, onSelect = { viewModel.setKind(it) })
                Spacer(Modifier.height(20.dp))
                BigAmount(amount = state.amount, kind = state.kind)
                Spacer(Modifier.height(20.dp))

                AccountPicker(
                    accounts = accounts,
                    selectedId = state.accountId,
                    onSelect = { viewModel.setAccount(it) },
                )
                if (state.kind == TxnKind.TRANSFER) {
                    AccountPicker(
                        accounts = accounts,
                        selectedId = state.toAccountId,
                        onSelect = { viewModel.setToAccount(it) },
                        label = "To",
                    )
                }
                CategoryPicker(
                    categories = categories,
                    selectedId = state.categoryId,
                    onSelect = { viewModel.setCategory(it) },
                )
                FieldRow(
                    label = "When",
                    value = formatDateTime(state.whenMs),
                    onClick = { /* date-time picker — P0d polish; for now keep "now" */ },
                    palette = palette,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.merchantName,
                    onValueChange = { viewModel.setMerchant(it) },
                    label = { Text("Merchant", color = palette.muted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                NotesField(value = state.notes, onChange = { viewModel.setNotes(it) })
                Spacer(Modifier.height(12.dp))
                TagChipInput(
                    tags = state.tagNames,
                    onAdd = { viewModel.addTag(it) },
                    onRemove = { viewModel.removeTag(it) },
                    palette = palette,
                )
                Spacer(Modifier.height(12.dp))
                AttachmentRow(
                    hasAttachment = state.attachmentBytes != null,
                    onPick = {
                        coroutineScope.launch {
                            val picked = container.imagePicker.pickFromGallery()
                            if (picked != null) viewModel.setAttachment(picked.bytes, picked.mimeType)
                        }
                    },
                    onClear = { viewModel.clearAttachment() },
                    palette = palette,
                )
                FieldRow(
                    label = "Split",
                    value = if (state.splits.isEmpty()) "Single entry" else "${state.splits.size} parts",
                    onClick = { showSplitSheet = true },
                    palette = palette,
                )
                if (state.kind in setOf(TxnKind.LEND, TxnKind.BORROW)) {
                    Spacer(Modifier.height(12.dp))
                    FieldRow(
                        label = "Person",
                        value = state.newPersonName ?: state.personId?.let { "selected" } ?: "Add",
                        onClick = { showPersonSheet = true },
                        palette = palette,
                    )
                    FieldRow(
                        label = "Due date",
                        value = state.dueDate?.let { formatDate(it) } ?: "Optional",
                        onClick = { /* date picker — P0d polish */ },
                        palette = palette,
                    )
                }
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(16.dp))
            }

            // Pinned keypad + save
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                NumericKeypad(onKey = { viewModel.setAmount(applyAmountKey(state.amount, it)) })
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (state.isSaving) "Saving…" else "Save entry",
                    onClick = { viewModel.save(onDone) },
                    enabled = state.isValid && !state.isSaving,
                )
            }
        }
    }
```

- [ ] **Step 3: Fix imports.** Add `import app.hisaab.design.components.PrimaryButton`. `applyAmountKey`, `KindChipRow`, `BigAmount`, `NumericKeypad` are same-package (no import). Remove imports that are now unused only if the compiler/IDE flags them (warnings only). Keep `HisaabColors` import (the private `FieldRow` uses it). `AmountField`/`KindSelector` remain defined in `EntryFields.kt` (unused now — leave them; a later phase removes them).

- [ ] **Step 4: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL. Resolve any missing import minimally.

- [ ] **Step 5: Full unit suite** (no logic changed): `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest` → PASS.

- [ ] **Step 6: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryScreen.kt
git commit -m "feat(entry): keypad-anchored Midnight Entry (big amount + numeric keypad)"
```

---

## Task 4: Gallery keypad demo + verification

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt`

- [ ] **Step 1: Add a keypad demo** to the gallery (stateful) so it renders in isolation. Add imports `app.hisaab.screens.entry.{BigAmount, NumericKeypad, KindChipRow, applyAmountKey}`, `app.hisaab.domain.TxnKind`, and `androidx.compose.runtime.{getValue, setValue, mutableStateOf, remember}`. Append inside the gallery Column:

```kotlin
            var amt by remember { mutableStateOf("1250") }
            KindChipRow(kind = TxnKind.EXPENSE, onSelect = {})
            BigAmount(amount = amt, kind = TxnKind.EXPENSE)
            NumericKeypad(onKey = { amt = applyAmountKey(amt, it) })
```

- [ ] **Step 2: Compile.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Controller visual check.** Controller temporarily mounts `ComponentGallery()` in MainActivity, **wakes the emulator** (`adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard`; confirm `mWakefulness=Awake`), builds, installs, screenshots (verify the big amount renders grouped + colored, and the keypad is a grid of surface keys), then reverts MainActivity. (See the saved asleep-emulator gotcha.)

- [ ] **Step 4: Commit.**
```bash
git add composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt
git commit -m "feat(entry): gallery keypad demo"
```

---

## Done criteria for Phase 4

- `AmountKeyTest` passes (10/10); full `commonTest` suite green.
- EntryScreen compiles and renders keypad-anchored: big amount colored by kind, Midnight kind chips, 3×4 keypad, lime Save; all detail fields + sheets still work; `EntryViewModel`/`EntryFormState`/repos untouched.
- Keypad demo renders correctly in the gallery.

**Next phase:** `2026-05-31-midnight-phase-5-home.md` — port Today + Month/Insights (hero balance card, in/out bar, account strip, per-day bar chart, category bars, budget rings) onto the Midnight primitives.
