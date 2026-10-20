package app.hisaab.mac

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * macOS companion entry point. Loads the Supabase config, builds the [CompanionController]
 * (login → recovery-phrase pairing → push loop), and renders [CompanionApp].
 */
fun main() = application {
    val scope = rememberCoroutineScope()
    val controller = remember { CompanionController(config = Config.load(), scope = scope).also { it.resume() } }
    Window(onCloseRequest = ::exitApplication, title = "Hisaab Companion") {
        MaterialTheme {
            CompanionApp(controller)
        }
    }
}
