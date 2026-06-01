package app.hisaab.screens.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.hisaab.LocalAppContainer
import app.hisaab.crypto.BIP39_WORDLIST
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.MidnightTextField
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SectionHeader
import app.hisaab.design.components.SurfaceCard
import kotlinx.coroutines.launch

@Composable
fun RecoveryEntryScreen(onRecovered: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val words = remember { mutableStateListOf<String>().apply { repeat(24) { add("") } } }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

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
        SectionHeader(
            title = "Enter your 24-word recovery phrase",
            eyebrow = "Recover",
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "We never store this. It must match the phrase shown when you first set up Hisaab.",
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall,
        )

        error?.let { msg ->
            Spacer(Modifier.height(8.dp))
            SurfaceCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    msg,
                    color = palette.negative,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed((0 until 24).toList()) { index, _ ->
                MidnightTextField(
                    value = words[index],
                    onValueChange = { words[index] = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "${index + 1}",
                    singleLine = true,
                    imeAction = ImeAction.Next,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Restore",
            onClick = { attemptRestore() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            loading = isLoading,
        )
    }
}
