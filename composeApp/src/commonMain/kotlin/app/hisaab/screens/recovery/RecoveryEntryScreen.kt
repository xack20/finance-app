package app.hisaab.screens.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.LocalAppContainer
import app.hisaab.crypto.BIP39_WORDLIST
import app.hisaab.design.LocalHisaabPalette
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
        modifier = Modifier.fillMaxSize().background(palette.background).padding(22.dp),
    ) {
        Text("Recover", color = palette.accent)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your 24-word recovery phrase",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "We never store this. It must match the phrase shown when you first set up Hisaab.",
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((0 until 24).toList()) { index ->
                OutlinedTextField(
                    value = words[index],
                    onValueChange = { words[index] = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${index + 1}", color = palette.accent) },
                    singleLine = true,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { attemptRestore() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.background)
            else Text("Restore", color = palette.background)
        }
    }
}
