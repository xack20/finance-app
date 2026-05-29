package app.hisaab.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabColors

/**
 * Shared label/value row with an optional tap target and a trailing divider.
 * Used by both [SettingsScreen] and [AutoCaptureScreen] to avoid duplication.
 */
@Composable
fun SettingRow(
    label: String,
    value: String,
    palette: HisaabColors.Palette,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.muted, modifier = Modifier.weight(1f))
        Text(value, color = palette.onBackground)
    }
    HorizontalDivider(color = palette.rule)
}
