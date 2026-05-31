package app.hisaab.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

@Composable
fun SplashScreen() {
    val palette = LocalHisaabPalette.current
    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HisaabSpacing.xl),
        ) {
            Text(
                text = "হিসাব",
                color = palette.accent,
                style = MaterialTheme.typography.displayMedium,
            )
            SplashPulseDots()
        }
    }
}

@Composable
private fun SplashPulseDots() {
    val palette = LocalHisaabPalette.current
    val transition = rememberInfiniteTransition(label = "splash-pulse")

    // Three dots with staggered phase offsets: 0 ms, 200 ms, 400 ms
    val delayOffsets = listOf(0, 200, 400)
    val alphas = delayOffsets.map { delayMs ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = delayMs),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot-alpha-$delayMs",
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm)) {
        alphas.forEach { alphaState ->
            val alpha by alphaState
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(palette.accent),
            )
        }
    }
}
