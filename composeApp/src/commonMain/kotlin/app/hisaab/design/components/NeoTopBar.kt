package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.LocalHisaabPalette

/**
 * The Midnight top bar (neo-ui.jsx NTopBar): a centered display title, an optional circular glass
 * chevron-left [onBack] button, and an optional lime [rightLabel] text action. Replaces Material
 * `TopAppBar` (left-aligned "Back" text) on detail/sub screens.
 */
@Composable
fun NeoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    rightLabel: String? = null,
    onRight: () -> Unit = {},
    rightColor: Color? = null,
) {
    val p = LocalHisaabPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(70.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(p.glass)
                        .border(1.dp, p.hair, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { HisaabIcon("chevron-left", tint = p.onBackground, size = 20.dp) }
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = p.onBackground,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        )
        Box(Modifier.widthIn(min = 70.dp), contentAlignment = Alignment.CenterEnd) {
            if (rightLabel != null) {
                TextButton(onClick = onRight, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                    Text(
                        rightLabel,
                        color = rightColor ?: p.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
