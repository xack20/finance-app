package app.hisaab.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class BiometricAuth(private val activity: FragmentActivity) {

    actual fun isAvailable(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult =
        suspendCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                        cont.resume(BiometricResult.Success)
                    }
                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        cont.resume(
                            if (code == BiometricPrompt.ERROR_USER_CANCELED ||
                                code == BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                                BiometricResult.UserCancelled
                            else BiometricResult.Error(msg.toString())
                        )
                    }
                    override fun onAuthenticationFailed() {
                        // Individual attempt failed — not terminal, prompt stays open
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build(),
            )
        }
}
