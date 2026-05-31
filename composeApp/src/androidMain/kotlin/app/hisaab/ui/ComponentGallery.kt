package app.hisaab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import app.hisaab.design.components.HCheck
import app.hisaab.design.components.HRadio
import app.hisaab.design.components.HToggle
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.MoneyTone
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SectionHeader
import app.hisaab.design.components.SurfaceCard
import app.hisaab.design.components.categoryHue

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
                    DockTab("TODAY", "◉", "Today"),
                    DockTab("MONTH", "◑", "Month"),
                    DockTab("PEOPLE", "○", "People"),
                    DockTab("SETTINGS", "⚙", "Settings"),
                ),
                selectedKey = "TODAY",
                onTabSelect = {},
                onFabClick = {},
            )
        }
    }
}
