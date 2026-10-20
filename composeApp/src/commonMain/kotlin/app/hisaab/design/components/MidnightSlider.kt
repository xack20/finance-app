package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/**
 * Midnight slider (neo-settings.jsx NSlider): a flat 7dp pill track — surfaceRaised base, lime fill —
 * and a 26dp white thumb ringed with a 3dp lime border. Custom thumb/track slots replace the Material
 * defaults (a bar thumb plus an end "stop indicator" dot) which don't match the design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidnightSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val p = LocalHisaabPalette.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        thumb = {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(p.onBackground, CircleShape)
                    .border(3.dp, p.accent, CircleShape),
            )
        },
        track = { state ->
            val span = (valueRange.endInclusive - valueRange.start).takeIf { it != 0f } ?: 1f
            val fraction = ((state.value - valueRange.start) / span).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(HisaabShapes.pill)
                    .background(p.surfaceRaised),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(7.dp)
                        .clip(HisaabShapes.pill)
                        .background(p.accent),
                )
            }
        },
    )
}
