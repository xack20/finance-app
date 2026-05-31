# Midnight Redesign — Phase 5: Home (Today + Month) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the Today and Month/Insights screens to Midnight — hero net card + in/out bar + glyph-chip feed (Today); net card + per-day bar chart + category bars + budget rings + recurring (Month) — reusing the Phase 2/3 primitives and **changing no ViewModel, repository, or domain type**.

**Architecture:** Pure presentation restyle. Today/Month ViewModels and the data shapes (`MoneyTotals`, `MonthlyTotals`, `CategorySlice`, `DayBucket`, `RecurringHit`, `BudgetProgress`) are untouched. Screens compose `SurfaceCard`/`MoneyText`/`GlyphChip`/`Eyebrow`/`SectionHeader`. Charts stay in `screens/month/` and are restyled in place; budgets convert from a linear list to Canvas rings.

**Surface-drift note (important):** the current `TodayViewModel` exposes **today's net + recent feed** (NO total-balance, NO per-account/account-strip data). The Midnight prototype's Today shows a *total-balance* hero + account strip. Per the redesign rule (keep the app's elements, restyle to the prototype's appearance), Phase 5 restyles **today's net** into the Midnight hero card + in/out bar and does **NOT** add total-balance or the account strip (those need new ViewModel/repo queries — a separate feature, out of scope here).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.10.1.

**Spec:** `docs/superpowers/specs/2026-05-31-midnight-redesign-design.md` (§8). **Design source:** `docs/design/design_handoff_hisaab_midnight/README.md` (§Today, §Month).

**Scope:** Today + Month screens + their 4 charts. Migrating the detail-pickers' sheets onto `MidnightSheet` is a later phase. Do NOT touch `TodayViewModel`, `MonthViewModel`, repos, or `domain/*`.

---

## Reference (Midnight tokens for this phase)
- Hero card: `SurfaceCard`, `Eyebrow` title, big `MoneyText`/`displayLarge` amount, a positive `accentSoft`-style delta pill.
- In/out bar: 8dp tall, `RoundedCornerShape(999.dp)`, track `backgroundInset`, green (`positive`) segment width = `income/(income+expense)`, remainder `negative`.
- Per-day bar: tallest bar solid `accent`; others `accent.copy(alpha=0.28f)`; axis `৳0` / `৳{peak}` in `faint`.
- Budget ring: 64dp, stroke 6dp, track `backgroundInset`, progress color `accent` (<80%) → `gold` (≥80%) → `negative` (≥100%), center `%`.
- Category bar: name + mono amount + `%` + per-category-hue fill on a `backgroundInset` track.
- Feed/recurring rows: `hair`/`hair2` dividers, `GlyphChip` 40–44dp, `MoneyText(signed)`.

---

## Task 1: Per-day bar chart → Midnight (PerDayLineChart.kt)

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/month/PerDayLineChart.kt`

- [ ] **Step 1: Replace the file body.** Tallest bar solid lime, others lime@28%, rounded tops, `৳0`/`৳peak` axis.

```kotlin
package app.hisaab.screens.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.DayBucket
import app.hisaab.util.toTaka

@Composable
fun PerDayLineChart(buckets: List<DayBucket>, palette: HisaabColors.Palette) {
    if (buckets.isEmpty()) {
        Text("No spending this month", color = palette.muted, fontSize = 13.sp)
        return
    }
    val maxAmount = buckets.maxOf { it.total }
    val minDay = buckets.minOf { it.epochDay }
    val maxDay = buckets.maxOf { it.epochDay }
    val dayRange = (maxDay - minDay).coerceAtLeast(1L)
    val accent = palette.accent
    val dim = palette.accent.copy(alpha = 0.28f)

    Column {
        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val slot = size.width / (dayRange + 1)
                val barWidth = (slot * 0.6f).coerceIn(3f, 14f)
                buckets.forEach { bucket ->
                    val xFrac = (bucket.epochDay - minDay).toFloat() / dayRange.toFloat()
                    val x = xFrac * (size.width - barWidth)
                    val heightFrac = (bucket.total / maxAmount).toFloat()
                    val barHeight = (heightFrac * size.height).coerceAtLeast(6f)
                    drawRoundRect(
                        color = if (bucket.total >= maxAmount) accent else dim,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("৳0", color = palette.faint, fontSize = 11.sp)
            Text("${maxAmount.toTaka()} peak", color = palette.faint, fontSize = 11.sp)
        }
    }
}
```

- [ ] **Step 2: Compile.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL. (Fall back to `/usr/libexec/java_home`.)

- [ ] **Step 3: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/month/PerDayLineChart.kt && git commit -m "feat(month): Midnight per-day bar chart (tallest lime, others lime@28%)"`

---

## Task 2: Budget rings (BudgetProgressList.kt → Canvas rings)

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/month/BudgetProgressList.kt`

- [ ] **Step 1: Replace the file body** with circular rings (Canvas `drawArc`), laid out in a wrapping row.

```kotlin
package app.hisaab.screens.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.BudgetProgress

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetProgressList(budgets: List<BudgetProgress>, palette: HisaabColors.Palette) {
    if (budgets.isEmpty()) {
        Text("Set a budget in Settings → Budgets.", color = palette.muted, fontSize = 13.sp)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        budgets.forEach { BudgetRing(it, palette) }
    }
}

@Composable
private fun BudgetRing(progress: BudgetProgress, palette: HisaabColors.Palette) {
    val pct = progress.percent.toFloat()
    val ringColor = when {
        pct >= 100f -> palette.negative
        pct >= 80f -> palette.gold
        else -> palette.accent
    }
    val track = palette.backgroundInset
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(64.dp)) {
                val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                val inset = 6.dp.toPx() / 2f
                val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                val topLeft = Offset(inset, inset)
                drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = stroke)
                drawArc(color = ringColor, startAngle = -90f,
                    sweepAngle = 360f * (pct.coerceAtMost(100f) / 100f), useCenter = false,
                    topLeft = topLeft, size = arcSize, style = stroke)
            }
            Text(
                "${progress.percent.toInt()}%",
                color = if (pct >= 100f) palette.negative else palette.onBackground,
                fontSize = 13.sp,
            )
        }
        Text(progress.budget.categoryName, color = palette.muted, fontSize = 12.sp)
        Text(
            "৳${progress.spent.toInt()}/৳${progress.budget.monthlyCapAmount.toInt()}",
            color = palette.faint, fontSize = 11.sp,
        )
    }
}
```

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL. If `BudgetProgress.budget.categoryName`/`monthlyCapAmount` field names differ, read `domain/Budget.kt` and use the actual names (do NOT change the domain type).

- [ ] **Step 3: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/month/BudgetProgressList.kt && git commit -m "feat(month): Midnight budget rings (Canvas drawArc, escalating color)"`

---

## Task 3: Category bars + recurring restyle

**Files:** Modify `screens/month/CategoryBarChart.kt` and `screens/month/RecurringList.kt`

- [ ] **Step 1: Read both files.** They are plain-Compose (no Canvas).

- [ ] **Step 2: `CategoryBarChart` restyle** — keep the existing structure but: render the amount as a grouped figure (`"৳${slice.total.toTaka()}"` via `app.hisaab.util.toTaka`), the `%` in `palette.faint`, and the progress bar on a `palette.backgroundInset` track (instead of `palette.rule`) with the per-category hue fill via the existing `parseColorOrAccent`. Pill the bar with `RoundedCornerShape(999.dp)`, height 7dp.

- [ ] **Step 3: `RecurringList` restyle** — keep the rows; change dividers to `palette.hair`, merchant to `palette.onBackground` 15sp, the "n× · avg ৳…" sub to `palette.muted` (use `it.avgAmount.toTaka()` for the amount), and the last-seen date to `palette.faint`.

- [ ] **Step 4: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/month/CategoryBarChart.kt composeApp/src/commonMain/kotlin/app/hisaab/screens/month/RecurringList.kt && git commit -m "feat(month): Midnight category bars + recurring restyle"`

---

## Task 4: Month screen layout (MonthScreen.kt)

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/month/MonthScreen.kt`

- [ ] **Step 1: Read `MonthScreen.kt` in full.** Keep the ViewModel wiring + the `selectMonth`/`previousMonth`/`nextMonth` events verbatim.

- [ ] **Step 2: Restyle the layout** (UI only): wrap the net section + each chart section in `app.hisaab.design.components.SurfaceCard`, precede each with an `app.hisaab.design.components.Eyebrow` ("Net this month" / "Spending by category" / "Budgets" / "Recurring"). The net amount → `app.hisaab.design.components.MoneyText(totals.net, signed = true, style = MaterialTheme.typography.displayLarge)`; the vs-last-month delta (`totals.net - totals.previousMonthNet`) → a small `accentSoft`-tinted pill with `MoneyText(delta, signed = true)`. Keep the month switcher (`‹`/`›` text buttons + "May 2026") colored with palette tokens. The four chart composables (`PerDayLineChart`, `CategoryBarChart`, `BudgetProgressList`, `RecurringList`) are called with the SAME arguments as today.

- [ ] **Step 3: Compile + full unit suite.** `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS.

- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/month/MonthScreen.kt && git commit -m "feat(month): Midnight Insights layout (net card + section cards)"`

---

## Task 5: Today screen restyle (TodayScreen.kt)

**Files:** Modify `composeApp/src/commonMain/kotlin/app/hisaab/screens/today/TodayScreen.kt`

- [ ] **Step 1: Read `TodayScreen.kt` in full.** Keep the ViewModel wiring, the `onTxnClick`/`onReview`/`onAutoCapture` callbacks, `dismissOptIn()`, and the `CaptureOptInCard` usage.

- [ ] **Step 2: Restyle (UI only):**
  - Header: `SectionHeader(title = "Today", eyebrow = "<greeting or date>")` + keep `ReviewBadge`.
  - **Hero net card:** wrap the totals in a `SurfaceCard`: `Eyebrow("Net today")` → `MoneyText(todayNet.net, signed = true, style = MaterialTheme.typography.displayLarge)` → an **in/out bar** (a `Row` of two weighted `Box`es: green `positive` weight = `income`, red `negative` weight = `expense`, on a `backgroundInset` track, 8dp tall, `RoundedCornerShape(999.dp)`; if both are 0, show a full `backgroundInset` bar) → an In/Out label row with `Eyebrow("In")` + `MoneyText(income)` … `MoneyText(expense)` + `Eyebrow("Out")`.
  - **Review banner:** if `pendingCount > 0`, a clickable `accentSoft`-tinted `SurfaceCard` (border via `accent`): "$pendingCount to review" + "Auto-captured from SMS" + a `›` in `accent`, `onClick = onReview`.
  - Capture opt-in card: keep `CaptureOptInCard` as-is (conditional).
  - **Feed:** keep the `LazyColumn` over `recent`; restyle each row to: a `GlyphChip(icon, hue = categoryHue(display.categoryColor))` + a `Column` (merchant `onBackground`/600 + "accountName · categoryName" `muted` 13sp) + trailing `MoneyText(row.amount, signed = true)` (preserve the existing sign-by-kind logic — pass the correctly-signed value). Divider = `palette.hair`. Keep the auto-capture indicator. Empty state stays (`muted` text).
  - For the feed `GlyphChip` icon: the project has no material-icons; categories carry a *color* not an icon vector, so use one shared simple `ImageVector` (e.g. a small filled square via `ImageVector.Builder` + `path`, like the gallery's `DemoGlyph`) — the chip's hue is the per-category signal.

- [ ] **Step 3: Compile + full unit suite.** `... compileDebugKotlinAndroid` then `... testDebugUnitTest` → BUILD SUCCESSFUL / PASS.

- [ ] **Step 4: Commit.** `git add composeApp/src/commonMain/kotlin/app/hisaab/screens/today/TodayScreen.kt && git commit -m "feat(today): Midnight Today (hero net card + in/out bar + glyph feed)"`

---

## Task 6: Gallery chart demos + verification

**Files:** Modify `composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt`

- [ ] **Step 1: Add chart demos** with dummy data so the charts render in isolation (the live screens need Authenticated state). Add imports for `PerDayLineChart`, `BudgetProgressList`, and the domain types `DayBucket`, `BudgetProgress`/`BudgetRow` (read their constructors and build 2–4 synthetic items). Pass `LocalHisaabPalette.current`. Append inside the gallery Column:

```kotlin
            Eyebrow("Charts")
            PerDayLineChart(
                buckets = listOf(0L, 1L, 2L, 3L, 4L).map { DayBucket(epochDay = 20000L + it, total = (it + 1) * 400.0) },
                palette = LocalHisaabPalette.current,
            )
            // BudgetProgressList(budgets = <2-3 synthetic BudgetProgress>, palette = LocalHisaabPalette.current)
```
Build the `BudgetProgressList` demo from the real `BudgetProgress`/`BudgetRow` constructors (read the data classes). These two are the new Canvas work and matter most to verify.

- [ ] **Step 2: Compile.** `... compileDebugKotlinAndroid` → BUILD SUCCESSFUL.

- [ ] **Step 3: Controller visual check.** Controller temporarily mounts `ComponentGallery()` in MainActivity, **wakes the emulator** (`adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard`; confirm `mWakefulness=Awake`), builds, installs, screenshots (verify per-day bars with one solid-lime tallest bar; budget rings with escalating color + center %), then reverts MainActivity.

- [ ] **Step 4: Commit.** `git add composeApp/src/androidMain/kotlin/app/hisaab/ui/ComponentGallery.kt && git commit -m "feat(month): gallery chart demos"`

---

## Done criteria for Phase 5

- Today + Month compile and render in Midnight: Today hero net card + in/out bar + glyph feed; Month net card + lime per-day bars + hued category bars + budget rings + recurring.
- Full `commonTest` suite green; no ViewModel/repo/domain change.
- Gallery renders the per-day bar chart + budget rings correctly.

**Next phase:** `2026-05-31-midnight-phase-6-assistant-people.md` — Assistant chat (glowing orb, mic, suggestion chips) + People list/detail.
