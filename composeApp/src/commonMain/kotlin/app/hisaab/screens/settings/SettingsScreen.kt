package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.HToggle
import app.hisaab.design.components.MidnightDialog
import app.hisaab.design.components.MidnightSheet
import app.hisaab.design.components.SectionHeader
import app.hisaab.design.components.SurfaceCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onBudgets: () -> Unit,
    onRecoveryReveal: () -> Unit,
    onAutoCapture: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember { SettingsViewModel(container) }
    val lockMs by viewModel.lockTimeoutMs.collectAsState()
    val biometricOn by viewModel.biometricEnabled.collectAsState()
    var showLockSheet by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        SectionHeader("Settings")
        Spacer(Modifier.height(32.dp))

        // — Privacy group —
        Eyebrow("Privacy", Modifier.padding(bottom = 8.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            SettingRow(
                label = "Lock timeout",
                value = formatLockTimeout(lockMs),
                palette = palette,
                onClick = { showLockSheet = true },
            )
            // Biometric row: HToggle replaces Switch
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Biometric unlock", color = palette.onBackground, modifier = Modifier.weight(1f))
                HToggle(
                    checked = biometricOn,
                    onCheckedChange = { viewModel.setBiometricEnabled(it) },
                )
            }
            HorizontalDivider(color = palette.hair)
            // Recovery phrase row: value "Reveal" shown in accent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRecoveryReveal() }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recovery phrase", color = palette.onBackground, modifier = Modifier.weight(1f))
                Text("Reveal", color = palette.accent)
            }
            HorizontalDivider(color = palette.hair)
        }

        Spacer(Modifier.height(24.dp))

        // — Data group —
        Eyebrow("Data", Modifier.padding(bottom = 8.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            SettingRow(label = "Accounts",   value = "›", palette = palette, onClick = onAccounts)
            SettingRow(label = "Categories", value = "›", palette = palette, onClick = onCategories)
            SettingRow(label = "Budgets",    value = "›", palette = palette, onClick = onBudgets)
        }

        Spacer(Modifier.height(24.dp))

        // — Capture group —
        Eyebrow("Capture", Modifier.padding(bottom = 8.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            SettingRow(label = "Auto-capture", value = "›", palette = palette, onClick = onAutoCapture)
        }

        Spacer(Modifier.height(24.dp))

        // — About group —
        Eyebrow("About", Modifier.padding(bottom = 8.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            SettingRow(label = "Version", value = "0.1.0-p0c", palette = palette, onClick = null)
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = { showSignOutDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Sign out", color = palette.negative)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showLockSheet) {
        val options = listOf(
            0L to "Immediate",
            30_000L to "30 seconds",
            300_000L to "5 minutes",
            Long.MAX_VALUE to "Never",
        )
        MidnightSheet(
            onDismiss = { showLockSheet = false },
            title = "Lock timeout",
        ) {
            options.forEach { (ms, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setLockTimeoutMs(ms); showLockSheet = false }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = palette.onBackground, modifier = Modifier.weight(1f))
                    if (ms == lockMs) Text("✓", color = palette.accent)
                }
                HorizontalDivider(color = palette.hair)
            }
        }
    }

    if (showSignOutDialog) {
        MidnightDialog(
            onDismiss = { showSignOutDialog = false },
            title = "Sign out of Hisaab?",
            body = "This will clear your encrypted data on this device. Make sure your 24-word recovery phrase is saved.",
            confirmLabel = "Sign out",
            onConfirm = { viewModel.signOut(onSignedOut) },
            destructive = true,
        )
    }
}

private fun formatLockTimeout(ms: Long): String = when (ms) {
    0L -> "Immediate"
    30_000L -> "30 seconds"
    300_000L -> "5 minutes"
    Long.MAX_VALUE -> "Never"
    else -> "${ms / 1000}s"
}
