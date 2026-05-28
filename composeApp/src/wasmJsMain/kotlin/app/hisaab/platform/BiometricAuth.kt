package app.hisaab.platform

actual class BiometricAuth {
    actual fun isAvailable(): Boolean = false
    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult =
        BiometricResult.NotAvailable
}
