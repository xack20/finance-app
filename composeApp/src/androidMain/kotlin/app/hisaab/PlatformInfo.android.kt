package app.hisaab

import android.os.Build

actual class PlatformInfo {
    actual val name: String = "Android ${Build.VERSION.SDK_INT}"
}
