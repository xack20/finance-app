package app.hisaab.screens.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.LocalReduceMotion

/** Glowing lime sparkle tile (assistant avatar / empty-state hero). */
@Composable
fun SparkleOrb(modifier: Modifier = Modifier, size: Int = 38) {
    val p = LocalHisaabPalette.current
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(p.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text("✦", color = p.accent, fontSize = (size * 0.5f).sp)
    }
}

/** Three pulsing lime dots — the assistant "typing" indicator. */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val reduceMotion = LocalReduceMotion.current

    if (reduceMotion) {
        // Reduce-motion: 3 dots at static alpha — no infinite transition.
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.6f)))
            }
        }
    } else {
        val t = rememberInfiniteTransition(label = "typing")
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                val a by t.animateFloat(
                    0.3f, 1f,
                    infiniteRepeatable(tween(600, delayMillis = i * 180), RepeatMode.Reverse),
                    label = "dot$i",
                )
                Box(Modifier.size(7.dp).clip(CircleShape).background(p.accent.copy(alpha = a)))
            }
        }
    }
}

/** Five pulsing lime bars — the mic "listening" equalizer. */
@Composable
fun ListeningEqualizer(modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    val reduceMotion = LocalReduceMotion.current

    if (reduceMotion) {
        // Reduce-motion: 5 bars at static mid height — no infinite transition.
        // Mid = (min + max) / 2 = (6f + (6f + (i%3)*6f + 8f)) / 2 per bar index.
        val staticHeights = listOf(10f, 13f, 16f, 10f, 13f)
        Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            staticHeights.forEach { h ->
                Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
            }
        }
    } else {
        val t = rememberInfiniteTransition(label = "eq")
        Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { i ->
                val h by t.animateFloat(
                    6f, 6f + (i % 3) * 6f + 8f,
                    infiniteRepeatable(tween(700, delayMillis = i * 100), RepeatMode.Reverse),
                    label = "bar$i",
                )
                Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
            }
        }
    }
}

/** Horizontal scrollable suggestion chips; tap -> [onPick]. Purely presentational. */
@Composable
fun SuggestionChips(suggestions: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { s ->
            Text(
                s,
                color = p.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(p.glass)
                    .border(1.dp, p.hair, RoundedCornerShape(999.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}
