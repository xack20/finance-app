package app.hisaab.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun OnboardingGraph(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()

    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(
                onSendOtp = { phone -> viewModel.sendOtp(phone) },
                isLoading = state.isLoading,
                error = state.error,
            )
        }
        composable("otp") {
            OtpScreen(
                phone = state.phone,
                onVerify = { token -> viewModel.verifyOtp(token) },
                onResend = { viewModel.sendOtp(state.phone) },
                isLoading = state.isLoading,
                error = state.error,
            )
        }
        composable("biometric") {
            BiometricSetupScreen(
                isAvailable = true,
                onEnroll = {
                    viewModel.generateMasterSecret { _ ->
                        navController.navigate("recovery_phrase")
                    }
                },
                onSkip = {
                    viewModel.generateMasterSecret { _ ->
                        navController.navigate("recovery_phrase")
                    }
                },
            )
        }
        composable("recovery_phrase") {
            RecoveryPhraseScreen(
                words = viewModel.generateRecoveryPhrase(),
                onAcknowledged = {
                    viewModel.acknowledgePhraseWrittenDown()
                    navController.navigate("profile")
                },
            )
        }
        composable("profile") {
            ProfileSetupScreen(
                onComplete = { _, _ -> onComplete() },
                isLoading = state.isLoading,
            )
        }
    }

    LaunchedEffect(state.otpSent) {
        if (state.otpSent) navController.navigate("otp") { launchSingleTop = true }
    }
    LaunchedEffect(state.otpVerified) {
        if (state.otpVerified) navController.navigate("biometric") { launchSingleTop = true }
    }
}
