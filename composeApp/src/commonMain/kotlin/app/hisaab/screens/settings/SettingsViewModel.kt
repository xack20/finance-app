package app.hisaab.screens.settings

import app.hisaab.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val container: AppContainer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _lockTimeoutMs = MutableStateFlow(
        container.secureStorage.loadString("lock_timeout_ms")?.toLongOrNull() ?: 30_000L
    )
    val lockTimeoutMs: StateFlow<Long> = _lockTimeoutMs

    private val _biometricEnabled = MutableStateFlow(
        container.secureStorage.loadString("biometric_enabled") == "true"
    )
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled

    fun setLockTimeoutMs(ms: Long) {
        _lockTimeoutMs.value = ms
        container.secureStorage.storeString("lock_timeout_ms", ms.toString())
        // Note: AppViewModel reads lockTimeoutMs at construction; change applies on next app start.
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _biometricEnabled.value = enabled
        container.secureStorage.storeString("biometric_enabled", enabled.toString())
    }

    fun signOut(onDone: () -> Unit) {
        scope.launch {
            container.authRepository.signOut()
            container.closeDatabase()
            container.secureStorage.clearMasterSecret()
            container.secureStorage.storeString("biometric_enabled", "false")
            onDone()
        }
    }
}
