package app.hisaab.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.PrimaryButton
import app.hisaab.platform.BiometricResult
import kotlinx.coroutines.launch

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var noEnroll by remember { mutableStateOf(false) }

    // Loads the persisted master_secret, opens the SQLCipher DB, and proceeds. Shared by the
    // biometric-success path and the no-biometric path.
    suspend fun openAndContinue() {
        val secret = container.secureStorage.loadMasterSecret()
        if (secret == null) {
            isLoading = false
            error = "Unable to load encryption key. Sign in again."
            return
        }
        container.openDatabase(secret)
        secret.fill(0)
        isLoading = false
        onUnlock()
    }

    fun attemptUnlock() {
        if (isLoading) return
        isLoading = true
        scope.launch {
            // If the user never enabled biometric (skipped at onboarding), the encrypted
            // master_secret in secure storage is the only gate — opening it directly is consistent
            // with that choice and avoids stranding them behind a biometric prompt they can't satisfy
            // (also the path that lets cold-start unlock succeed on devices without enrolled biometrics).
            val biometricEnabled = container.secureStorage.loadString("biometric_enabled") == "true"
            if (!biometricEnabled) {
                openAndContinue()
                return@launch
            }
            val result = container.biometricAuth.authenticate(
                title = "Unlock Hisaab",
                subtitle = "Confirm your identity to continue",
            )
            when (result) {
                BiometricResult.Success -> openAndContinue()
                BiometricResult.UserCancelled -> {
                    isLoading = false
                    // Leave on LockScreen — user can tap the button to retry.
                }
                BiometricResult.NotAvailable -> {
                    isLoading = false
                    // Design's neg "No fingerprints enrolled" variant.
                    noEnroll = true
                    error = null
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

    val tileShape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Lock tile — surface square with hairline border and lime lock stroke icon.
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(tileShape)
                    .background(palette.surface, tileShape)
                    .border(1.dp, palette.hair, tileShape),
                contentAlignment = Alignment.Center,
            ) {
                HisaabIcon("lock", tint = palette.accent, size = 36.dp)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Lime হিসাব wordmark above the headline.
                Text(
                    text = "হিসাব",
                    color = palette.accent,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Hisaab is locked",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                    color = palette.onBackground,
                )
                // Only the neg variant shows an eyebrow (no fingerprints enrolled).
                if (noEnroll) Eyebrow("No fingerprints enrolled", color = palette.negative)
            }

            error?.let { msg ->
                Text(
                    text = msg,
                    color = palette.negative,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            PrimaryButton(
                text = "Unlock with biometric",
                leadingIcon = "finger",
                onClick = { attemptUnlock() },
                enabled = !isLoading,
                loading = isLoading,
                fillMaxWidth = false,
                modifier = Modifier.width(260.dp),
            )
        }
    }
}
