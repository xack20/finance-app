package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onBudgets: () -> Unit,
    onRecoveryReveal: () -> Unit,
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
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = palette.onBackground)
        Spacer(Modifier.height(32.dp))

        SectionLabel("Privacy", palette)
        SettingRow("Lock timeout", formatLockTimeout(lockMs), palette) { showLockSheet = true }
        SettingToggleRow("Biometric unlock", biometricOn, palette) { viewModel.setBiometricEnabled(it) }
        SettingRow("Recovery phrase", "Reveal", palette) { onRecoveryReveal() }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Data", palette)
        SettingRow("Accounts", "›", palette, onClick = onAccounts)
        SettingRow("Categories", "›", palette, onClick = onCategories)
        SettingRow("Budgets", "›", palette, onClick = onBudgets)

        Spacer(Modifier.height(24.dp))
        SectionLabel("About", palette)
        SettingRow("Version", "0.1.0-p0c", palette, onClick = null)
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { showSignOutDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign out", color = palette.negative)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showLockSheet) {
        LockTimeoutSheet(
            current = lockMs,
            onPick = { viewModel.setLockTimeoutMs(it); showLockSheet = false },
            onDismiss = { showLockSheet = false },
            palette = palette,
        )
    }
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.signOut(onSignedOut)
                }) { Text("Sign out", color = palette.negative) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel", color = palette.muted) }
            },
            text = {
                Text("This will clear your encrypted data on this device. Make sure your 24-word recovery phrase is saved.")
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, palette: HisaabColors.Palette) {
    Text(
        text.uppercase(),
        color = palette.accent,
        letterSpacing = 2.sp,
        fontSize = 11.sp,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    palette: HisaabColors.Palette,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.muted, modifier = Modifier.weight(1f))
        Text(value, color = palette.onBackground)
    }
    HorizontalDivider(color = palette.rule)
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    palette: HisaabColors.Palette,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.muted, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.background,
                checkedTrackColor = palette.accent,
            ),
        )
    }
    HorizontalDivider(color = palette.rule)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockTimeoutSheet(
    current: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    palette: HisaabColors.Palette,
) {
    val sheetState = rememberModalBottomSheetState()
    val options = listOf(
        0L to "Immediate",
        30_000L to "30 seconds",
        300_000L to "5 minutes",
        Long.MAX_VALUE to "Never",
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
    ) {
        Column(modifier = Modifier.padding(22.dp).fillMaxWidth()) {
            Text("Lock timeout", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            options.forEach { (ms, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(ms) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = palette.onBackground, modifier = Modifier.weight(1f))
                    if (ms == current) Text("✓", color = palette.accent)
                }
                HorizontalDivider(color = palette.rule)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatLockTimeout(ms: Long): String = when (ms) {
    0L -> "Immediate"
    30_000L -> "30 seconds"
    300_000L -> "5 minutes"
    Long.MAX_VALUE -> "Never"
    else -> "${ms / 1000}s"
}
