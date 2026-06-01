package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/**
 * The Midnight card: surface fill, hairline border, 22dp radius, 16dp inner padding.
 * [glass] = true uses the translucent glass fill instead of the solid surface.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    shape: RoundedCornerShape = HisaabShapes.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalHisaabPalette.current
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (glass) p.glass else p.surface, shape)
            .border(1.dp, p.hair, shape)
            .padding(HisaabSpacing.lg),
        content = content,
    )
}

/** Convenience alias for the translucent glass variant. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    SurfaceCard(modifier = modifier, glass = true, content = content)
