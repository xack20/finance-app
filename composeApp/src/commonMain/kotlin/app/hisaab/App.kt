package app.hisaab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.hisaab.design.HisaabTheme
import app.hisaab.platform.LifecycleEvent
import app.hisaab.screens.LockScreen
import app.hisaab.screens.SplashScreen
import app.hisaab.screens.onboarding.OnboardingGraph
import app.hisaab.screens.onboarding.OnboardingViewModel
import app.hisaab.screens.recovery.RecoveryEntryScreen
import app.hisaab.screens.today.TodayScreen

@Composable
fun App() {
    val container = LocalAppContainer.current
    HisaabTheme {
        val appViewModel = remember {
            AppViewModel(
                authRepository = container.authRepository,
                hasMasterSecret = { container.secureStorage.loadMasterSecret() != null },
                onLock = { container.closeDatabase() },
            ).also { it.init() }
        }
        val onboardingViewModel = remember { OnboardingViewModel(container) }

        LaunchedEffect(Unit) {
            container.lifecycle.events().collect { ev ->
                when (ev) {
                    LifecycleEvent.Background -> appViewModel.onAppBackground()
                    LifecycleEvent.Foreground -> appViewModel.onAppForeground()
                }
            }
        }

        val state by appViewModel.state.collectAsState()
        when (state) {
            is AppState.Loading -> SplashScreen()
            is AppState.Unauthenticated,
            is AppState.Onboarding -> OnboardingGraph(
                viewModel = onboardingViewModel,
                onComplete = { appViewModel.onOnboardingComplete() },
            )
            is AppState.OnboardingKey -> RecoveryEntryScreen(
                onRecovered = { appViewModel.onBiometricUnlockSuccess() },
            )
            is AppState.Locked -> LockScreen(
                onUnlock = { appViewModel.onBiometricUnlockSuccess() },
            )
            is AppState.Authenticated -> TodayScreen()
        }
    }
}
