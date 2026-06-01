package app.hisaab.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.design.components.HisaabIcon

/**
 * Shared label/value row (the design's NRow) with an optional tap target and a trailing divider.
 * [sub] adds a muted line under the label; [valueColor] tints the value (defaults to muted);
 * [chevron] draws a faint chevron-right icon (replacing the old literal "›" string). Used by
 * [SettingsScreen] and [AutoCaptureScreen].
 */
@Composable
fun SettingRow(
    label: String,
    value: String,
    palette: HisaabColors.Palette,
    sub: String? = null,
    valueColor: Color? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = palette.onBackground)
            if (sub != null) {
                Text(sub, color = palette.muted, fontSize = 13.sp)
            }
        }
        if (value.isNotEmpty()) Text(value, color = valueColor ?: palette.muted)
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            HisaabIcon("chevron-right", tint = palette.faint, size = 19.dp)
        }
    }
    HorizontalDivider(color = palette.hair)
}
