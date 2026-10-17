package app.hisaab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.hisaab.design.HisaabTheme
import app.hisaab.design.components.MidnightDialog
import app.hisaab.platform.LifecycleEvent
import app.hisaab.screens.LockScreen
import app.hisaab.screens.SplashScreen
import app.hisaab.screens.onboarding.OnboardingGraph
import app.hisaab.screens.onboarding.OnboardingViewModel
import app.hisaab.screens.recovery.RecoveryEntryScreen
import app.hisaab.screens.main.MainGraph

@Composable
fun App() {
    val container = LocalAppContainer.current
    HisaabTheme {
        val appViewModel = remember {
            AppViewModel(
                authRepository = container.authRepository,
                hasMasterSecret = { container.secureStorage.loadMasterSecret() != null },
                onLock = { container.lockUi() },
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
            is AppState.Authenticated -> MainGraph()
        }

        // Testers install from Drive without adb: surface the last crash once so they can copy and send it.
        var crashReport by remember { mutableStateOf(container.lastCrashReport()) }
        crashReport?.let { report ->
            val clipboard = LocalClipboardManager.current
            val dismiss = { container.clearCrashReport(); crashReport = null }
            MidnightDialog(
                onDismiss = dismiss,
                title = "Hisaab crashed last time",
                body = "Tap Copy and paste it to your developer.\n\n" + report.lineSequence().take(4).joinToString("\n"),
                confirmLabel = "Copy",
                onConfirm = { clipboard.setText(AnnotatedString(report)); dismiss() },
                dismissLabel = "Dismiss",
            )
        }
    }
}
