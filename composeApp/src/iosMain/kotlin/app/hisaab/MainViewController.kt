package app.hisaab

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

// One container per app process — Compose host stays alive for the whole launch.
private val iosContainer: AppContainer by lazy { AppContainer() }

fun MainViewController(): UIViewController = ComposeUIViewController {
    CompositionLocalProvider(LocalAppContainer provides iosContainer) {
        App()
    }
}
