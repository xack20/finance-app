package app.hisaab.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.NeoTopBar
import app.hisaab.design.components.PrimaryButton

/**
 * Consent screen for cloud parsing. The caller (AutoCaptureScreen flow in MainGraph) owns the
 * [AutoCaptureViewModel] and passes plain data / callbacks here so no second VM is instantiated.
 *
 * @param providerName  Display name of the selected cloud provider (e.g. "Claude").
 * @param consentGranted Whether consent has already been granted (cloudConsentAt != null).
 * @param onGrant  Called when the user taps "I agree". Caller records consent and navigates up once it's
 *                 saved (AutoCaptureViewModel.recordConsent's `then`) — navigating first cancels the write.
 * @param onRevoke Called when the user taps "Revoke consent". Caller forwards to AutoCaptureViewModel.revokeConsent().
 * @param onBack   Navigate up.
 */
@Composable
fun CloudConsentScreen(
    providerName: String,
    consentGranted: Boolean,
    onGrant: () -> Unit,
    onRevoke: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalHisaabPalette.current

    Column(Modifier.fillMaxSize().background(palette.background)) {
        NeoTopBar(title = "Cloud consent", onBack = onBack)
        Column(Modifier.fillMaxSize().padding(horizontal = HisaabSpacing.gutter)) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Every captured message — SMS, email and payment notifications — will be read by $providerName.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp,
                ),
                color = palette.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Hisaab's agent sends them in batches so it can add payments to your books by itself. Hisaab's own servers never see this data — it goes from your device to $providerName. One-time codes are always masked; redaction (on by default) also masks account and phone numbers. Promos and other non-payments are read too, then skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
            )
            Spacer(Modifier.height(26.dp))
            if (consentGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HisaabIcon("check", tint = palette.positive, size = 18.dp, strokeWidth = 2.4f)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Consent granted.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.positive,
                    )
                }
                Spacer(Modifier.height(18.dp))
                // Glass button with a neg-colored label (GlassButton has no text-color param).
                Button(
                    onClick = onRevoke,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = HisaabShapes.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.glass,
                        contentColor = palette.negative,
                    ),
                    border = BorderStroke(1.dp, palette.hair),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                ) { Text("Revoke consent") }
            } else {
                PrimaryButton(
                    text = "I agree — let the agent read my messages",
                    onClick = onGrant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
