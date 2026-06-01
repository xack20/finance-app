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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes
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
            .clip(HisaabShapes.pill)
            .background(p.surfaceRaised)
            .border(1.dp, p.hair, HisaabShapes.pill)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DockButton(tabs[0], selectedKey, onTabSelect, p)
        DockButton(tabs[1], selectedKey, onTabSelect, p)
        Box(
            Modifier
                .testTag("dock_fab")
                .padding(horizontal = 2.dp).size(52.dp).clip(CircleShape)
                .background(p.accent).clickable(onClick = onFabClick)
                .semantics { contentDescription = "Add"; role = Role.Button },
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
        Modifier
            .testTag("dock_${tab.key}")
            .size(48.dp).clip(HisaabShapes.pill)
            .background(if (active) p.accentSoft else Color.Transparent)
            .clickable { onSelect(tab) }
            .semantics { contentDescription = tab.label; role = Role.Tab; selected = active },
        contentAlignment = Alignment.Center,
    ) {
        Text(tab.icon, fontSize = 18.sp, color = if (active) p.accent else p.muted)
    }
}
