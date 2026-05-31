package app.hisaab.design.components

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hisaab.design.LocalHisaabPalette

/** Midnight slider: lime active track + thumb, surfaceRaised inactive track. */
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
        colors = SliderDefaults.colors(
            thumbColor = p.accent,
            activeTrackColor = p.accent,
            inactiveTrackColor = p.surfaceRaised,
            activeTickColor = p.onAccent,
            inactiveTickColor = p.hair,
        ),
    )
}
