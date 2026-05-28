package app.hisaab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.hisaab.auth.StubAuthRepository
import app.hisaab.design.HisaabTheme
import app.hisaab.screens.LockScreen
import app.hisaab.screens.SplashScreen
import app.hisaab.screens.onboarding.OnboardingGraph
import app.hisaab.screens.onboarding.OnboardingViewModel
import app.hisaab.screens.today.TodayScreen

@Composable
fun App() {
    HisaabTheme {
        // TODO P0c: replace StubAuthRepository with SupabaseAuthRepository
        // once DI is wired and Supabase credentials are configured.
        val authRepository = remember { StubAuthRepository() }
        val appViewModel = remember {
            AppViewModel(
                authRepository = authRepository,
                hasMasterSecret = { false },
            ).also { it.init() }
        }
        val onboardingViewModel = remember { OnboardingViewModel(authRepository) }
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
