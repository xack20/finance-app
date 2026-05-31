package app.hisaab.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

/** Lime pill toggle (50x30, sliding 24dp knob). */
@Composable
fun HToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val knobX by animateDpAsState(if (checked) 23.dp else 3.dp, label = "toggleKnob")
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) p.accent else p.surfaceRaised)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = knobX).size(24.dp).clip(CircleShape)
                .background(if (checked) p.onAccent else p.muted),
        )
    }
}

/** 26dp rounded checkbox; lime fill + check when checked. */
@Composable
fun HCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Box(
        modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) p.accent else p.surface)
            .border(1.dp, if (checked) p.accent else p.hair, RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = p.onAccent)
    }
}

/** 22dp radio; accent ring + lime dot when selected. */
@Composable
fun HRadio(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) p.accent else p.hair, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(p.accent))
    }
}
