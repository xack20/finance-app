package app.hisaab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.HisaabTheme
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.DockTab
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.FloatingDock
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.GlassCard
import app.hisaab.design.components.GlyphChip
import app.hisaab.design.components.GradientAvatar
import app.hisaab.design.components.HCheck
import app.hisaab.design.components.HRadio
import app.hisaab.design.components.HToggle
import app.hisaab.design.components.MidnightSlider
import app.hisaab.design.components.MidnightTextField
import app.hisaab.design.components.OtpCells
import app.hisaab.design.components.RecoveryWordGrid
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.MoneyTone
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SectionHeader
import app.hisaab.design.components.SurfaceCard
import app.hisaab.design.components.categoryHue
import app.hisaab.domain.BudgetProgress
import app.hisaab.domain.BudgetRow
import app.hisaab.domain.DayBucket
import app.hisaab.domain.TxnKind
import app.hisaab.domain.YearMonth
import app.hisaab.screens.entry.BigAmount
import app.hisaab.screens.entry.KindChipRow
import app.hisaab.screens.entry.NumericKeypad
import app.hisaab.screens.entry.applyAmountKey
import app.hisaab.screens.agent.ListeningEqualizer
import app.hisaab.screens.agent.SparkleOrb
import app.hisaab.screens.agent.SuggestionChips
import app.hisaab.screens.agent.TypingDots
import app.hisaab.screens.month.BudgetProgressList
import app.hisaab.screens.month.PerDayBarChart

/** Trivial tintable glyph (avoids a material-icons dependency) for the gallery demo only. */
private val DemoGlyph: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(7f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(7f, 19f); close()
    }
}.build()

/**
 * Debug-only gallery rendering the Midnight component primitives inside [HisaabTheme].
 * Usable as a Compose @Preview in the IDE, or temporarily mounted in MainActivity for an
 * on-device screenshot during Phase 2 verification. Not referenced by production code.
 */
@Composable
@Preview
fun ComponentGallery() {
    HisaabTheme {
        Column(
            Modifier.fillMaxSize().background(LocalHisaabPalette.current.background)
                .verticalScroll(rememberScrollState()).padding(HisaabSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HisaabSpacing.lg),
        ) {
            SectionHeader("Components", eyebrow = "Midnight")
            SurfaceCard {
                Eyebrow("Total balance")
                MoneyText(62250.0, signed = true)
                MoneyText(-1250.0)
                MoneyText(0.0, tone = MoneyTone.Plain)
            }
            GlassCard { Text("Glass card") }
            Row(horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm)) {
                GlyphChip(DemoGlyph, categoryHue("violet"))
                GlyphChip(DemoGlyph, categoryHue("teal"))
                GlyphChip(DemoGlyph, categoryHue("amber"))
            }
            PrimaryButton("Continue", onClick = {})
            PrimaryButton("Disabled", onClick = {}, enabled = false)
            GlassButton("Maybe later", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.md)) {
                HToggle(checked = true, onCheckedChange = {})
                HToggle(checked = false, onCheckedChange = {})
                HCheck(checked = true, onCheckedChange = {})
                HRadio(selected = true, onClick = {})
            }
            FloatingDock(
                tabs = listOf(
                    DockTab("TODAY", "today", "Today"),
                    DockTab("MONTH", "month", "Month"),
                    DockTab("PEOPLE", "people", "People"),
                    DockTab("SETTINGS", "gear", "Settings"),
                ),
                selectedKey = "TODAY",
                onTabSelect = {},
                onFabClick = {},
            )
            var amt by remember { mutableStateOf("1250") }
            KindChipRow(kind = TxnKind.EXPENSE, onSelect = {})
            BigAmount(amount = amt, kind = TxnKind.EXPENSE)
            NumericKeypad(onKey = { amt = applyAmountKey(amt, it) })

            Eyebrow("Charts")
            PerDayBarChart(
                buckets = listOf(400.0, 1200.0, 300.0, 900.0, 1800.0, 600.0)
                    .mapIndexed { i, v -> DayBucket(epochDay = 20000L + i, total = v) },
                palette = LocalHisaabPalette.current,
            )
            BudgetProgressList(
                budgets = listOf("Food" to 45.0, "Transport" to 85.0, "Shopping" to 112.0)
                    .mapIndexed { i, (name, pct) ->
                        BudgetProgress(
                            budget = BudgetRow(
                                id = "b$i", categoryId = "c$i", categoryName = name,
                                monthlyCapAmount = 5000.0, currency = "BDT",
                                startsMonth = YearMonth("2026-05"), archivedAt = null, createdAt = 0L,
                            ),
                            spent = 5000.0 * pct / 100.0,
                            percent = pct,
                        )
                    },
                palette = LocalHisaabPalette.current,
            )

            Eyebrow("Assistant + People")
            Row(horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm)) {
                GradientAvatar("Arif Hasan")
                GradientAvatar("Karim Uddin")
                SparkleOrb(size = 44)
                TypingDots()
                ListeningEqualizer()
            }
            SuggestionChips(listOf("Set a food budget", "I paid 500 for lunch"), onPick = {})

            Eyebrow("MidnightSlider")
            var conf by remember { mutableStateOf(0.85f) }
            MidnightSlider(value = conf, onValueChange = { conf = it }, valueRange = 0.5f..0.99f)
            Text(
                "Auto-post ≥ ${(conf * 100).toInt()}%",
                color = LocalHisaabPalette.current.muted,
            )

            Eyebrow("MidnightTextField")
            var demoName by remember { mutableStateOf("") }
            MidnightTextField(value = demoName, onValueChange = { demoName = it }, label = "Your name", placeholder = "Name")
            var demoPhone by remember { mutableStateOf("") }
            MidnightTextField(value = demoPhone, onValueChange = { demoPhone = it }, label = "Phone number", placeholder = "1X XXXX XXXX", prefix = "+880", keyboardType = KeyboardType.Phone)

            Eyebrow("OtpCells + NumericKeypad")
            var demoCode by remember { mutableStateOf("") }
            OtpCells(value = demoCode)
            Spacer(Modifier.height(12.dp))
            app.hisaab.design.components.NumericKeypad(
                onDigit = { if (demoCode.length < 6) demoCode += it },
                onBackspace = { demoCode = demoCode.dropLast(1) },
            )

            Eyebrow("RecoveryWordGrid")
            RecoveryWordGrid(words = listOf("abandon","ability","able","about","above","absent","absorb","abstract","absurd","abuse","access","accident"), modifier = Modifier.height(220.dp))

            Eyebrow("PrimaryButton states")
            PrimaryButton(text = "Loading", onClick = {}, loading = true)
            PrimaryButton(text = "Start", trailingGlyph = "→", onClick = {})
        }
    }
}
