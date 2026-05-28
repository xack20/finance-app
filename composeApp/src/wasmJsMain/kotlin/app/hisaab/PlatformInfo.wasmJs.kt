package app.hisaab

import kotlinx.browser.window

actual class PlatformInfo {
    actual val name: String = "Web · ${window.navigator.userAgent.take(40)}"
}
