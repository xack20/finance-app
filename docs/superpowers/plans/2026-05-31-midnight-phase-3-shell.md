# Midnight Redesign — Phase 3: Shell & Modal Primitives — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the authenticated shell's flat Material `NavigationBar` + dropdown FAB with the Midnight **floating glass dock + center lime FAB chooser**, and add reusable **MidnightSheet / MidnightDialog / Toggle / Check / Radio** primitives — all without changing navigation behavior or any ViewModel.

**Architecture:** New composables under `design/components/`. The dock is wired into the existing `MainGraph()` by swapping its `Scaffold.bottomBar` + `floatingActionButton` for an overlaid floating dock, **keeping the exact `navController.navigate(...) { popUpTo…; launchSingleTop; restoreState }` blocks verbatim** (selection is derived from `currentRoute`, no new state). Modal primitives wrap stock Material3 `ModalBottomSheet`/`AlertDialog` with Midnight tokens. The project has **no material-icons dependency** — dock tabs use the existing text-glyph convention (`MainTab.iconChar()`). Verification is by compilation + a component-gallery screenshot (the live shell needs the Authenticated state, which onboarding/OTP gates; the gallery renders the dock in isolation).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.10.1, Material3.

**Spec:** `docs/superpowers/specs/2026-05-31-midnight-redesign-design.md` (§6, §7). **Design source:** `docs/design/design_handoff_hisaab_midnight/README.md` (§Global structure & navigation, §Sheet/Dialog/Toggle, §Motion).

**Scope:** Shell chrome + modal/form primitives only. The **keypad Entry** rebuild is Phase 4. Migrating the 13 existing sheet/dialog call sites onto the new primitives happens during the per-screen port phases (4–8); this phase only builds the primitives and wires the dock. Do NOT touch `App.kt`, `AppViewModel`, or any ViewModel.

---

## Reference values (from the design map, dark tokens)

- **Dock:** floating pill, `bottom ≈ 14dp`, centered; fill = `palette.surfaceRaised`, `1dp palette.hair` border, `HisaabShapes.pill`, inner padding 8–10dp. Tabs **Today / Month / People / Settings** split 2 + FAB + 2. Each tab button 48dp, radius 999; active = `accentSoft` bg + `accent` glyph; inactive = transparent + `muted` glyph. Center **FAB** 52dp lime circle, `+` glyph in `onAccent`. Tab screens reserve ~`106dp` bottom padding.
- **Sheet:** `HisaabShapes.sheet` (26dp top), `palette.background` fill, top grab handle `38×4dp` radius 99 `hair`, optional eyebrow title, body padding 20dp.
- **Dialog:** `palette.surface` fill, 24dp radius (card shape); confirm = `accent` (or `negative` if destructive), dismiss = `muted`.
- **Toggle:** `50×30dp` track radius 999; on = `accent` track + `onAccent` knob; off = `surfaceRaised` track + `muted` knob; knob `24dp`. **Check:** `26dp` radius 8, lime fill + check when on. **Radio:** `22dp` circle, `accent` ring + lime dot when on.

---

## Task 1: MidnightSheet + MidnightDialog

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightDialog.kt`

- [ ] **Step 1: Implement MidnightSheet.**

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/**
 * Midnight bottom sheet: 26dp top radius, background fill, hairline grab handle, optional eyebrow
 * title. Drop-in replacement for raw ModalBottomSheet call sites. [content] is a ColumnScope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidnightSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalHisaabPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.background,
        shape = HisaabShapes.sheet,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = HisaabSpacing.md), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(99.dp)).background(p.hair))
            }
        },
    ) {
        Column(modifier.fillMaxWidth().padding(horizontal = HisaabSpacing.gutter).padding(bottom = HisaabSpacing.xl)) {
            if (title != null) {
                Eyebrow(title, Modifier.padding(bottom = HisaabSpacing.sm))
            }
            content()
        }
    }
}
```

- [ ] **Step 2: Implement MidnightDialog.**

```kotlin
package app.hisaab.design.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/**
 * Midnight confirm dialog: surface fill, 22dp radius (card shape), accent confirm (or negative when
 * [destructive]), muted dismiss. Replaces raw AlertDialog confirm/sign-out/delete call sites.
 */
@Composable
fun MidnightDialog(
    onDismiss: () -> Unit,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    val p = LocalHisaabPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.onBackground,
        textContentColor = p.muted,
        shape = HisaabShapes.card,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) p.negative else p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = p.muted) }
        },
    )
}
```

- [ ] **Step 3: Compile.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL. (Fall back to `/usr/libexec/java_home`.)

- [ ] **Step 4: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightSheet.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/design/components/MidnightDialog.kt
git commit -m "feat(design): MidnightSheet + MidnightDialog modal primitives"
```

---

## Task 2: Toggle + Check + Radio form controls

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/FormControls.kt`

- [ ] **Step 1: Implement.**

```kotlin
package app.hisaab.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

/** Lime pill toggle (50×30, sliding 24dp knob). */
@Composable
fun HToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val knobX by animateDpAsState(if (checked) 23.dp else 3.dp, label = "toggleKnob")
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) p.accent else p.surfaceRaised)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = knobX).size(24.dp).clip(CircleShape)
                .background(if (checked) p.onAccent else p.muted),
        )
    }
}

/** 26dp rounded checkbox; lime fill + check when checked. */
@Composable
fun HCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Box(
        modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) p.accent else p.surface)
            .border(1.dp, if (checked) p.accent else p.hair, RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = p.onAccent)
    }
}

/** 22dp radio; accent ring + lime dot when selected. */
@Composable
fun HRadio(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) p.accent else p.hair, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(p.accent))
    }
}
```

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/FormControls.kt
git commit -m "feat(design): HToggle + HCheck + HRadio form controls"
```

---

## Task 3: FloatingDock + FAB chooser

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/components/FloatingDock.kt`

This is a self-contained, stateless dock. It does NOT know about navigation — the caller (MainGraph, Task 4) passes the selected key, the tab list, and callbacks. Icons are text glyphs (caller supplies them), matching the app's `MainTab.iconChar()` convention.

- [ ] **Step 1: Implement.**

```kotlin
package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette

/** One dock tab: a stable [key] (e.g. the route name), a text-glyph [icon], and a [label]. */
data class DockTab(val key: String, val icon: String, val label: String)

/**
 * The Midnight floating glass dock: 4 tabs split 2 + center lime FAB + 2. Stateless — the caller
 * supplies [tabs], the [selectedKey], and the callbacks. Place it bottom-center over content.
 */
@Composable
fun FloatingDock(
    tabs: List<DockTab>,
    selectedKey: String?,
    onTabSelect: (DockTab) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalHisaabPalette.current
    require(tabs.size == 4) { "FloatingDock expects exactly 4 tabs" }
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(p.surfaceRaised)
            .border(1.dp, p.hair, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DockButton(tabs[0], selectedKey, onTabSelect, p)
        DockButton(tabs[1], selectedKey, onTabSelect, p)
        Box(
            Modifier.padding(horizontal = 2.dp).size(52.dp).clip(CircleShape)
                .background(p.accent).clickable(onClick = onFabClick),
            contentAlignment = Alignment.Center,
        ) { Text("+", color = p.onAccent, fontSize = 26.sp) }
        DockButton(tabs[2], selectedKey, onTabSelect, p)
        DockButton(tabs[3], selectedKey, onTabSelect, p)
    }
}

@Composable
private fun DockButton(
    tab: DockTab,
    selectedKey: String?,
    onSelect: (DockTab) -> Unit,
    p: HisaabColors.Palette,
) {
    val active = tab.key == selectedKey
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(999.dp))
            .background(if (active) p.accentSoft else Color.Transparent)
            .clickable { onSelect(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Text(tab.icon, fontSize = 18.sp, color = if (active) p.accent else p.muted)
    }
}
```

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/components/FloatingDock.kt
git commit -m "feat(design): FloatingDock primitive (glass pill + center lime FAB)"
```

---

## Task 4: Wire the FloatingDock into MainGraph

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/screens/main/MainGraph.kt`

Replace the `Scaffold.bottomBar` (`NavigationBar`, ~lines 70–101) and `floatingActionButton` (~lines 103–134) with the floating dock overlaid on the content. **Keep the `NavHost` and every route/`navigate` block exactly as-is.**

- [ ] **Step 1: Read the current `MainGraph.kt`** in full so you preserve the NavHost + routes verbatim.

- [ ] **Step 2: Restructure the shell.** Make these precise edits:

  (a) Remove the `bottomBar = { ... }` and `floatingActionButton = { ... }` arguments from the `Scaffold(...)`. Keep `containerColor = palette.background` and the content lambda.

  (b) Inside the content lambda, keep the existing `Box { NavHost(...) ... AutoPostSnackbarHost(...) }`, give the `NavHost` bottom padding so content clears the dock, and add the dock + chooser overlaid bottom-center. The content lambda becomes:

```kotlin
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = MainTab.TODAY.name,
                modifier = Modifier.fillMaxSize().padding(bottom = if (showNavAndFab) 106.dp else 0.dp),
            ) {
                // ... ALL existing composable(route) { ... } entries UNCHANGED ...
            }

            AutoPostSnackbarHost(/* ...existing args, unchanged... */)

            if (showNavAndFab) {
                var showChooser by remember { mutableStateOf(false) }
                val tabs = MainTab.entries.map { DockTab(it.name, it.iconChar(), it.label()) }
                FloatingDock(
                    tabs = tabs,
                    selectedKey = currentRoute,
                    onTabSelect = { tab ->
                        navController.navigate(tab.key) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onFabClick = { showChooser = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                )
                if (showChooser) {
                    MidnightSheet(onDismiss = { showChooser = false }, title = "Add") {
                        DockChooserRow("Add manually", palette.onBackground) {
                            showChooser = false; navController.navigate("entry")
                        }
                        DockChooserRow("Ask the assistant", palette.accent) {
                            showChooser = false; navController.navigate(AGENT_ROUTE)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockChooserRow(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    androidx.compose.material3.Text(
        text,
        color = color,
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
```

  (c) Add the needed imports: `app.hisaab.design.components.DockTab`, `app.hisaab.design.components.FloatingDock`, `app.hisaab.design.components.MidnightSheet`, `androidx.compose.ui.Alignment`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.fillMaxWidth`. Remove now-unused imports (`NavigationBar`, `NavigationBarItem`, `NavigationBarItemDefaults`, `FloatingActionButton`, `DropdownMenu`, `DropdownMenuItem`) for cleanliness. (Unused imports are warnings, not build errors here — the project builds with some pre-existing warnings — so this won't block compilation, but tidy them anyway.)

  (d) Keep `MainTab.iconChar()` and `MainTab.label()` (already private in this file).

- [ ] **Step 3: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL. Resolve any unused-import or missing-import issue minimally.

- [ ] **Step 4: Run the full unit suite** (no logic should have changed): `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest` → PASS.

- [ ] **Step 5: Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/main/MainGraph.kt
git commit -m "feat(shell): Midnight floating dock + FAB chooser in MainGraph (nav behavior unchanged)"
```

---

## Task 5: Gallery update + visual verification

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt`

- [ ] **Step 1: Add dock + form controls to the gallery** so they render in isolation (the live shell needs the Authenticated state). Append inside the gallery `Column`, before the closing brace, and add imports `app.hisaab.design.components.{DockTab, FloatingDock, HToggle, HCheck, HRadio}`:

```kotlin
            // Form controls
            Row(horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.md)) {
                HToggle(checked = true, onCheckedChange = {})
                HToggle(checked = false, onCheckedChange = {})
                HCheck(checked = true, onCheckedChange = {})
                HRadio(selected = true, onClick = {})
            }
            // Floating dock (isolated)
            FloatingDock(
                tabs = listOf(
                    DockTab("TODAY", "◉", "Today"),
                    DockTab("MONTH", "◑", "Month"),
                    DockTab("PEOPLE", "○", "People"),
                    DockTab("SETTINGS", "⚙", "Settings"),
                ),
                selectedKey = "TODAY",
                onTabSelect = {},
                onFabClick = {},
            )
```

- [ ] **Step 2: Compile.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Controller visual check.** The controller (not a subagent) temporarily mounts `ComponentGallery()` in MainActivity, **wakes the emulator first** (`adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard`; confirm `mWakefulness=Awake` and keyguard gone), builds, installs, screenshots, judges (dock = glass pill + lime FAB + active lime tab; toggles lime), then reverts MainActivity. (See the saved memory on the asleep-emulator black-frame gotcha.)

- [ ] **Step 4: Commit.**
```bash
git add composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt
git commit -m "feat(design): gallery demos for dock + form controls"
```

---

## Done criteria for Phase 3

- MidnightSheet, MidnightDialog, HToggle/HCheck/HRadio, FloatingDock all compile and live under `design/components/`.
- `MainGraph` renders the floating dock + FAB chooser; **navigation behavior is byte-for-byte preserved** (same `navigate{popUpTo;launchSingleTop;restoreState}`, same routes, same 30s-lock path in `AppViewModel` untouched).
- Full `commonTest` suite still green (no logic changed).
- Gallery renders the dock + controls in Midnight.

**Next phase:** `2026-05-31-midnight-phase-4-keypad-entry.md` — rebuild EntryScreen as the tactile numeric keypad (big live amount colored by kind, category quick-row, detail chips, 3×4 keypad), driving the existing `EntryViewModel` API unchanged.
