package app.hisaab.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

actual class BiometricAuth {

    actual fun isAvailable(): Boolean =
        LAContext().canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error = null,
        )

    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult =
        suspendCancellableCoroutine { cont ->
            LAContext().evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = title,
            ) { success, error ->
                cont.resume(
                    if (success) BiometricResult.Success
                    else BiometricResult.Error(error?.localizedDescription ?: "Authentication failed"),
                )
            }
        }
}
