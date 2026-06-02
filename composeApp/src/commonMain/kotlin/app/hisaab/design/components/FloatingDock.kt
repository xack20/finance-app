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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/** One dock tab: a stable [key] (e.g. the route name) and a HisaabIcon [icon] name + [label]. */
data class DockTab(val key: String, val icon: String, val label: String)

/** Translucent glass fill for the dock panel — rgba(18,20,26,.82) from neo.jsx:54. (True
 *  backdrop blur isn't available cross-platform in Compose; the translucent fill approximates it.) */
private val DockGlass = Color(0xD112141A)

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
    fabIcon: String = "plus",
) {
    val p = LocalHisaabPalette.current
    require(tabs.size == 4) { "FloatingDock expects exactly 4 tabs" }
    Row(
        modifier
            .shadow(20.dp, HisaabShapes.pill, clip = false) // 0 16px 40px -10px rgba(0,0,0,.7)
            .clip(HisaabShapes.pill)
            .background(DockGlass)
            .border(1.dp, p.hair, HisaabShapes.pill)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DockButton(tabs[0], selectedKey, onTabSelect, p)
        DockButton(tabs[1], selectedKey, onTabSelect, p)
        Box(
            Modifier
                .testTag("dock_fab")
                .padding(horizontal = 2.dp).size(52.dp)
                // --glow-lime: lime-tinted drop shadow + 1px lime ring approximates the CSS glow.
                .shadow(16.dp, CircleShape, clip = false, ambientColor = p.accent, spotColor = p.accent)
                .clip(CircleShape)
                .background(p.accent)
                .border(1.dp, p.accent.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onFabClick)
                .semantics { contentDescription = "Add"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { HisaabIcon(fabIcon, tint = p.onAccent, size = 26.dp, strokeWidth = 2.4f) }
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
        HisaabIcon(
            name = tab.icon,
            tint = if (active) p.accent else p.faint,
            size = 23.dp,
            strokeWidth = if (active) 2.1f else 1.8f,
            // Only the active "today" tab is filled, matching neo.jsx:63.
            filled = active && tab.icon == "today",
        )
    }
}
