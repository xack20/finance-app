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
import app.hisaab.design.LocalHisaabPalette
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Anyone with these 24 words can restore your data on another device. Keep them private.",
                color = palette.negative, fontSize = 12.sp,
            )
            Spacer(Modifier.height(20.dp))
            when {
                error != null -> Text(error!!, color = palette.negative)
                words == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(words!!) { i, word ->
                        Row(
                            modifier = Modifier
                                .border(1.dp, palette.rule, MaterialTheme.shapes.small)
                                .background(palette.surface, MaterialTheme.shapes.small)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", color = palette.accent, fontSize = 11.sp,
                                modifier = Modifier.width(20.dp))
                            Text(word, color = palette.onBackground, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
