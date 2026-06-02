package app.hisaab.mac

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * macOS companion entry point (P3 step 1 — scaffold only). Subsequent steps add the Messages
 * chat.db reader, Supabase pairing + push, and a menu-bar UI. The relay sealing logic is reused
 * verbatim from :sharedCore (RelayCaptureCrypto), so the ciphertext this app uploads is exactly
 * what the phone decrypts.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Hisaab Companion") {
        MaterialTheme {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Hisaab Mac companion — capture setup coming in the next steps.")
            }
        }
    }
}
