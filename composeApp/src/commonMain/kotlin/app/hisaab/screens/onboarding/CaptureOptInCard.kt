package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.rule, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text("Log transactions automatically", style = MaterialTheme.typography.titleMedium, color = palette.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "Hisaab can read your bKash, Nagad and bank SMS to log transactions for you — on-device by default, fully private. You can turn this off anytime.",
            color = palette.muted,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onTurnOn,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                Text("Turn on", color = palette.background)
            }
            TextButton(onClick = onMaybeLater) { Text("Maybe later", color = palette.muted) }
        }
    }
}
