package app.hisaab.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.platform.BiometricResult
import kotlinx.coroutines.launch

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun attemptUnlock() {
        if (isLoading) return
        isLoading = true
        scope.launch {
            val result = container.biometricAuth.authenticate(
                title = "Unlock Hisaab",
                subtitle = "Confirm your identity to continue",
            )
            when (result) {
                BiometricResult.Success -> {
                    val secret = container.secureStorage.loadMasterSecret()
                    if (secret == null) {
                        isLoading = false
                        error = "Unable to load encryption key. Sign in again."
                        return@launch
                    }
                    container.openDatabase(secret)
                    secret.fill(0)
                    isLoading = false
                    onUnlock()
                }
                BiometricResult.UserCancelled -> {
                    isLoading = false
                    // Leave on LockScreen — user can tap the button to retry.
                }
                BiometricResult.NotAvailable -> {
                    isLoading = false
                    error = "Biometric not available on this device."
                }
                is BiometricResult.Error -> {
                    isLoading = false
                    error = result.message
                }
            }
        }
    }

    // Auto-prompt on first composition.
    LaunchedEffect(Unit) { attemptUnlock() }

    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("Hisaab is locked", style = MaterialTheme.typography.headlineSmall, color = palette.onBackground)
            error?.let {
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { attemptUnlock() },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.background)
                else Text("Unlock with biometric", color = palette.background)
            }
        }
    }
}
