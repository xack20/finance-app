package app.hisaab

import platform.UIKit.UIDevice

actual class PlatformInfo {
    actual val name: String =
        "${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}"
}
