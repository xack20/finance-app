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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.CloudProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudConsentScreen(onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember {
        val service = container.captureService
        AutoCaptureViewModel(
            configRepo = container.captureConfigRepository,
            senderRepo = container.senderRepository,
            accountRepo = container.accountRepository,
            hasSmsPermission = { service.hasSmsPermission() },
            requestSmsPermission = { service.requestSmsPermission() },
            backfillSince = { cursor -> service.backfillSince(cursor) },
            loadApiKey = { key -> container.secureStorage.loadString(key) },
            storeApiKey = { key, value -> container.secureStorage.storeString(key, value) },
            clearApiKey = { key -> container.secureStorage.storeString(key, "") },
            router = container.llmRouter,
        )
    }
    val cfg by viewModel.config.collectAsState()
    val providerName = cfg?.cloudProvider?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
        ?: CloudProvider.CLAUDE.name.lowercase().replaceFirstChar { it.uppercase() }

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
            val granted = cfg?.cloudConsentAt != null
            if (granted) {
                Text("Consent granted.", color = palette.positive)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { viewModel.revokeConsent() }) {
                    Text("Revoke consent", color = palette.negative)
                }
            } else {
                Button(
                    onClick = { viewModel.recordConsent(); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                ) {
                    Text("I agree — use cloud parsing", color = palette.background)
                }
            }
        }
    }
}
