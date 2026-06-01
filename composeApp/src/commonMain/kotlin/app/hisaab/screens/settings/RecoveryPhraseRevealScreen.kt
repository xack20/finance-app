package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.NeoTopBar
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

    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        NeoTopBar(title = "Recovery phrase", onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = HisaabSpacing.gutter)) {
            Spacer(Modifier.height(8.dp))
            // Inline neg-toned warning row (no card), warn stroke icon.
            Row(verticalAlignment = Alignment.Top) {
                HisaabIcon(
                    "warn",
                    tint = palette.negative,
                    size = 17.dp,
                    modifier = Modifier.padding(end = 8.dp, top = 1.dp),
                )
                Text(
                    text = "Anyone with these 24 words can restore your data on another device. Keep them private.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    ),
                    color = palette.negative,
                )
            }
            val err = error
            val w = words
            when {
                err != null -> {
                    Spacer(Modifier.height(26.dp))
                    Eyebrow(err, color = palette.negative)
                }
                w == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = palette.accent)
                }
                else -> {
                    Spacer(Modifier.height(22.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        itemsIndexed(w) { i, word ->
                            Row(
                                modifier = Modifier
                                    .border(1.dp, palette.hair, RoundedCornerShape(12.dp))
                                    .background(palette.surface, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 13.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${i + 1}",
                                    color = palette.accent,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.width(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    word,
                                    color = palette.onBackground,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
