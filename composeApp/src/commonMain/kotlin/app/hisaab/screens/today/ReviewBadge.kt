package app.hisaab.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes

@Composable
fun ReviewBadge(
    count: Long,
    palette: HisaabColors.Palette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0L) return
    Text(
        text = "$count to review",
        color = palette.background,
        fontSize = 12.sp,
        modifier = modifier
            .clip(HisaabShapes.pill)
            .background(palette.accent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
