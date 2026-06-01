package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SurfaceCard

/**
 * Soft, skippable opt-in card. The host (onboarding completion screen or Today, depending on the
 * flow chosen in MainGraph) decides whether to show this based on a "capture_optin_seen" flag in
 * SecureStorage; both callbacks should set that flag so it never reappears.
 */
@Composable
fun CaptureOptInCard(
    onTurnOn: () -> Unit,
    onMaybeLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHisaabPalette.current
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        // Icon tile
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(palette.accentSoft, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✉",
                color = palette.accent,
                fontSize = 22.sp,
            )
        }

        Spacer(Modifier.height(HisaabSpacing.md))

        // Title — preserved verbatim
        Text(
            text = "Log transactions automatically",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.onBackground,
        )

        Spacer(Modifier.height(HisaabSpacing.sm))

        // Body — preserved verbatim
        Text(
            text = "Hisaab can read your bKash, Nagad and bank SMS to log transactions for you — on-device by default, fully private. You can turn this off anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
        )

        Spacer(Modifier.height(HisaabSpacing.lg))

        // Two-up CTA row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(
                text = "Turn on",
                onClick = onTurnOn,
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
            GlassButton(
                text = "Maybe later",
                onClick = onMaybeLater,
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
        }
    }
}
