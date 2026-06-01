package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.SurfaceCard
import app.hisaab.platform.BiometricResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryPhraseRevealScreen(onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            val result = container.biometricAuth.authenticate(
                title = "Show recovery phrase",
                subtitle = "Confirm to display your 24-word recovery phrase",
            )
            when (result) {
                BiometricResult.Success -> {
                    val secret = container.secureStorage.loadMasterSecret()
                    if (secret == null) {
                        error = "Master secret missing — try signing in again"
                        return@launch
                    }
                    words = container.mnemonicService.encode(secret)
                    secret.fill(0)
                }
                is BiometricResult.Error -> error = result.message
                BiometricResult.NotAvailable -> error = "Biometric not available on this device."
                BiometricResult.UserCancelled -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recovery phrase", color = palette.onBackground) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = palette.muted) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(padding)
                .padding(horizontal = HisaabSpacing.gutter),
        ) {
            Spacer(Modifier.height(12.dp))
            // Warning card — negative-toned SurfaceCard
            SurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                shape = HisaabShapes.card,
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "⚠",
                        color = palette.negative,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp, top = 1.dp),
                    )
                    Text(
                        text = "Anyone with these 24 words can restore your data on another device. Never screenshot or share them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.negative,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            val err = error
            val w = words
            when {
                err != null -> {
                    Eyebrow("Error")
                    Spacer(Modifier.height(6.dp))
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = palette.negative)
                }
                w == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = palette.accent)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(w) { i, word ->
                        Row(
                            modifier = Modifier
                                .border(1.dp, palette.hair, HisaabShapes.field)
                                .background(palette.surface, HisaabShapes.field)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${i + 1}",
                                color = palette.accent,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                word,
                                color = palette.onBackground,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
