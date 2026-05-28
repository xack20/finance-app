package app.hisaab

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

private val webContainer: AppContainer by lazy { AppContainer() }

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        CompositionLocalProvider(LocalAppContainer provides webContainer) {
            App()
        }
    }
}
