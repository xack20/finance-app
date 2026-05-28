package app.hisaab.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val palette = LocalHisaabPalette.current
    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Hisaab is locked",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.onBackground,
            )
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                Text("Unlock with biometric", color = palette.background)
            }
        }
    }
}
