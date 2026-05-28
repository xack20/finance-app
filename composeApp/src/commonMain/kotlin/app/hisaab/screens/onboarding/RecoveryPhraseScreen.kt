package app.hisaab.screens.onboarding

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
import app.hisaab.design.LocalHisaabPalette

@Composable
fun RecoveryPhraseScreen(
    words: List<String>,
    onAcknowledged: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    var acknowledged by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(palette.background).padding(22.dp)) {
        Text("Recovery", color = palette.accent)
        Spacer(Modifier.height(8.dp))
        Text(
            "Write these 24 words down",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "⚠ Lose these = lose your data",
            color = palette.negative,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(words) { index, word ->
                Row(
                    modifier = Modifier
                        .border(1.dp, palette.rule, MaterialTheme.shapes.small)
                        .background(palette.surface, MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        color = palette.accent,
                        fontSize = 11.sp,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(word, color = palette.onBackground, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.rule, MaterialTheme.shapes.small)
                .background(palette.surface, MaterialTheme.shapes.small)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { acknowledged = it },
                colors = CheckboxDefaults.colors(checkedColor = palette.accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "I've written down all 24 words in a safe place",
                color = palette.onBackground,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAcknowledged,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = acknowledged,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        ) {
            Text("I've saved them", color = palette.background)
        }
    }
}
