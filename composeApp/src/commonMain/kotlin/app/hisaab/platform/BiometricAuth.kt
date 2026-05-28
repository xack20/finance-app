package app.hisaab.platform

sealed class BiometricResult {
    data object Success : BiometricResult()
    data class Error(val message: String) : BiometricResult()
    data object NotAvailable : BiometricResult()
    data object UserCancelled : BiometricResult()
}

expect class BiometricAuth {
    suspend fun authenticate(title: String, subtitle: String): BiometricResult
    fun isAvailable(): Boolean
}
