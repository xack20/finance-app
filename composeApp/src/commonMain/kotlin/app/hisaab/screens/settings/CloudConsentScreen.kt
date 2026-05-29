package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

/**
 * Consent screen for cloud parsing. The caller (AutoCaptureScreen flow in MainGraph) owns the
 * [AutoCaptureViewModel] and passes plain data / callbacks here so no second VM is instantiated.
 *
 * @param providerName  Display name of the selected cloud provider (e.g. "Claude").
 * @param consentGranted Whether consent has already been granted (cloudConsentAt != null).
 * @param onGrant  Called when the user taps "I agree". Caller forwards to AutoCaptureViewModel.recordConsent().
 * @param onRevoke Called when the user taps "Revoke consent". Caller forwards to AutoCaptureViewModel.revokeConsent().
 * @param onBack   Navigate up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudConsentScreen(
    providerName: String,
    consentGranted: Boolean,
    onGrant: () -> Unit,
    onRevoke: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalHisaabPalette.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud consent", color = palette.onBackground) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = palette.muted) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(palette.background).padding(padding).padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Your bank SMS text will be sent to $providerName for parsing.",
                style = MaterialTheme.typography.titleMedium, color = palette.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Hisaab's own servers never see this data — it goes directly from your device to $providerName using your API key. Redaction is on by default and masks account and phone numbers before sending.",
                color = palette.muted,
            )
            Spacer(Modifier.height(24.dp))
            if (consentGranted) {
                Text("Consent granted.", color = palette.positive)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRevoke) {
                    Text("Revoke consent", color = palette.negative)
                }
            } else {
                Button(
                    onClick = { onGrant(); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                ) {
                    Text("I agree — use cloud parsing", color = palette.background)
                }
            }
        }
    }
}
