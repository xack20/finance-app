package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.PrimaryButton

@Composable
fun BiometricSetupScreen(
    isAvailable: Boolean,
    onEnroll: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    val palette = LocalHisaabPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = HisaabSpacing.gutter, vertical = HisaabSpacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Hero + copy — vertically centered in the top weight region
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 96dp accentSoft tile with the lime fingerprint stroke icon + soft lime glow.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(14.dp, RoundedCornerShape(28.dp), clip = false, ambientColor = palette.accent, spotColor = palette.accent)
                    .clip(RoundedCornerShape(28.dp))
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                HisaabIcon("finger", tint = palette.accent, size = 48.dp, strokeWidth = 1.7f)
            }

            Spacer(Modifier.height(HisaabSpacing.xl))

            Eyebrow("Secure", color = palette.accent)

            Spacer(Modifier.height(HisaabSpacing.sm))

            Text(
                "Unlock with your\nface or finger",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(HisaabSpacing.md))

            Text(
                "Your biometric unlocks Hisaab. Your data never leaves this device.",
                modifier = Modifier.widthIn(max = 280.dp),
                color = palette.muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            error?.let {
                Spacer(Modifier.height(HisaabSpacing.md))
                Text(it, color = palette.negative)
            }
        }

        // CTAs pinned to bottom
        Column(modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                text = if (isAvailable) "Enable biometric" else "Not available on this device",
                onClick = onEnroll,
                enabled = isAvailable && !isLoading,
                loading = isLoading,
            )
            Spacer(Modifier.height(HisaabSpacing.sm))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onSkip) {
                    Text("Skip (not recommended)", color = palette.muted)
                }
            }
        }
    }
}
