package app.hisaab.screens.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.hisaab.design.LocalHisaabPalette

@Composable
fun RecoveryEntryScreen(onRecovered: () -> Unit) {
    val palette = LocalHisaabPalette.current
    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Text("Recovery entry — full implementation in P0c-1 Task 9", color = palette.muted)
    }
}
