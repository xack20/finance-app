package app.hisaab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.hisaab.design.HisaabTheme
import app.hisaab.screens.LockScreen
import app.hisaab.screens.SplashScreen
import app.hisaab.screens.onboarding.OnboardingGraph
import app.hisaab.screens.onboarding.OnboardingViewModel
import app.hisaab.screens.today.TodayScreen

@Composable
fun App(container: AppContainer) {
    HisaabTheme {
        val appViewModel = remember {
            AppViewModel(
                authRepository = container.authRepository,
                hasMasterSecret = { container.masterSecretInMemory() != null },
            ).also { it.init() }
        }
        val onboardingViewModel = remember { OnboardingViewModel(container) }
        val state by appViewModel.state.collectAsState()

        when (val s = state) {
            is AppState.Loading -> SplashScreen()
            is AppState.Unauthenticated,
            is AppState.Onboarding -> OnboardingGraph(
                viewModel = onboardingViewModel,
                onComplete = { appViewModel.onOnboardingComplete() },
            )
            is AppState.Locked -> LockScreen(
                onUnlock = { appViewModel.onBiometricUnlockSuccess() },
            )
            is AppState.Authenticated,
            is AppState.OnboardingKey -> TodayScreen()
        }
    }
}
