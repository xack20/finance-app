package app.hisaab.screens.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.crypto.BIP39_WORDLIST
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.PrimaryButton
import kotlinx.coroutines.launch

@Composable
fun RecoveryEntryScreen(onRecovered: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val words = remember { mutableStateListOf<String>().apply { repeat(24) { add("") } } }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val filled = words.count { it.isNotBlank() }

    fun attemptRestore() {
        if (isLoading) return
        error = null
        val list = words.toList().map { it.trim().lowercase() }
        if (list.any { it.isBlank() }) {
            error = "Please fill in all 24 words"
            return
        }
        val unknown = list.filter { it !in BIP39_WORDLIST }
        if (unknown.isNotEmpty()) {
            error = "Not in BIP39 wordlist: ${unknown.take(3).joinToString(", ")}${if (unknown.size > 3) ", …" else ""}"
            return
        }
        isLoading = true
        scope.launch {
            try {
                val masterSecret = container.mnemonicService.decode(list)
                container.secureStorage.storeMasterSecret(masterSecret)
                container.secureStorage.storeString("biometric_enabled", "false")
                container.openDatabase(masterSecret)
                masterSecret.fill(0)
                isLoading = false
                onRecovered()
            } catch (e: Throwable) {
                isLoading = false
                error = "Invalid recovery phrase: ${e.message ?: "checksum failed"}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .imePadding()
            .padding(HisaabSpacing.gutter),
    ) {
        Eyebrow("Recover", color = palette.accent)
        Spacer(Modifier.height(10.dp))
        Text(
            "Enter your 24-word phrase",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
            color = palette.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        // Subtitle in mono/muted per design.
        Text(
            "We never store this. It must match the phrase from setup.",
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
        )

        error?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(msg, color = palette.negative, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Spacer(Modifier.height(18.dp))

        // 2-column grid of 24 numbered word inputs (inline mono-lime index, 48dp rows).
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items((0 until 24).toList()) { index ->
                WordInputCell(
                    index = index + 1,
                    value = words[index],
                    onValueChange = { words[index] = it },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            // Gated until all 24 are filled; the label shows progress.
            text = if (filled < 24) "Enter all 24 words ($filled/24)" else "Restore",
            onClick = { attemptRestore() },
            modifier = Modifier.fillMaxWidth(),
            enabled = filled == 24 && !isLoading,
            loading = isLoading,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WordInputCell(index: Int, value: String, onValueChange: (String) -> Unit) {
    val p = LocalHisaabPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.5.dp, p.hair, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$index",
            color = p.accent,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            cursorBrush = SolidColor(p.accent),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = p.onBackground,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
