package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

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
        modifier = Modifier.fillMaxSize().background(palette.background).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Secure", color = palette.accent)
            Spacer(Modifier.height(12.dp))
            Text(
                "Unlock with your\nface or finger",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Your biometric unlocks Hisaab. Your data never leaves this device.",
                color = palette.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = palette.negative)
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onEnroll,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = isAvailable && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                Text(
                    if (isAvailable) "Enable biometric" else "Not available on this device",
                    color = palette.background,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Skip (not recommended)", color = palette.muted)
            }
        }
    }
}
