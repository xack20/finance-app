package app.hisaab.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.CloudProvider
import app.hisaab.screens.capture.AutoPostSnackbarHost
import app.hisaab.screens.capture.ReviewInboxScreen
import app.hisaab.screens.entry.EntryScreen
import app.hisaab.screens.month.MonthScreen
import app.hisaab.screens.people.PeopleListScreen
import app.hisaab.screens.people.PersonDetailScreen
import app.hisaab.screens.settings.AccountsScreen
import app.hisaab.screens.settings.AutoCaptureScreen
import app.hisaab.screens.settings.AutoCaptureViewModel
import app.hisaab.screens.settings.BudgetsScreen
import app.hisaab.screens.settings.CategoriesScreen
import app.hisaab.screens.settings.CloudConsentScreen
import app.hisaab.screens.settings.RecoveryPhraseRevealScreen
import app.hisaab.screens.settings.SettingsScreen
import app.hisaab.screens.today.TodayScreen
import app.hisaab.screens.transaction.TransactionDetailScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGraph() {
    val palette = LocalHisaabPalette.current
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val showNavAndFab = currentRoute in setOf(
        MainTab.TODAY.name, MainTab.MONTH.name, MainTab.PEOPLE.name, MainTab.SETTINGS.name,
    )

    Scaffold(
        bottomBar = {
            if (showNavAndFab) {
                NavigationBar(containerColor = palette.surface) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.name,
                            onClick = {
                                navController.navigate(tab.name) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Text(
                                    tab.iconChar(),
                                    fontSize = 18.sp,
                                    color = if (currentRoute == tab.name) palette.accent else palette.muted,
                                )
                            },
                            label = { Text(tab.label()) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.accent,
                                selectedTextColor = palette.accent,
                                indicatorColor = palette.background,
                                unselectedIconColor = palette.muted,
                                unselectedTextColor = palette.muted,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute in setOf(MainTab.TODAY.name, MainTab.MONTH.name, MainTab.PEOPLE.name)) {
                FloatingActionButton(
                    onClick = { navController.navigate("entry") },
                    containerColor = palette.accent,
                ) {
                    Text("+", color = palette.background, fontSize = 28.sp)
                }
            }
        },
        containerColor = palette.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = MainTab.TODAY.name,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(MainTab.TODAY.name) {
                    TodayScreen(
                        onTxnClick = { id -> navController.navigate("txn/$id") },
                        onReview = { navController.navigate("review") },
                        onAutoCapture = { navController.navigate("settings/auto-capture") },
                    )
                }
                composable(MainTab.MONTH.name) { MonthScreen() }
                composable(MainTab.PEOPLE.name) {
                    PeopleListScreen(onPersonClick = { id -> navController.navigate("person/$id") })
                }
                composable("person/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    PersonDetailScreen(personId = id, onBack = { navController.popBackStack() })
                }
                composable(MainTab.SETTINGS.name) {
                    SettingsScreen(
                        onAccounts = { navController.navigate("settings/accounts") },
                        onCategories = { navController.navigate("settings/categories") },
                        onBudgets = { navController.navigate("settings/budgets") },
                        onRecoveryReveal = { navController.navigate("settings/recovery") },
                        onAutoCapture = { navController.navigate("settings/auto-capture") },
                        onSignedOut = { /* AppViewModel state change handles redirect via App.kt */ },
                    )
                }
                composable("settings/accounts") { AccountsScreen(onBack = { navController.popBackStack() }) }
                composable("settings/categories") { CategoriesScreen(onBack = { navController.popBackStack() }) }
                composable("settings/budgets") { BudgetsScreen(onBack = { navController.popBackStack() }) }
                composable("settings/recovery") { RecoveryPhraseRevealScreen(onBack = { navController.popBackStack() }) }
                composable("settings/auto-capture") {
                    AutoCaptureScreen(
                        onBack = { navController.popBackStack() },
                        onConsent = { navController.navigate("settings/auto-capture/consent") },
                    )
                }
                composable("settings/auto-capture/consent") {
                    // Build a minimal AutoCaptureViewModel scoped to this entry so we can read
                    // the live config (providerName, consentGranted) without duplicating state.
                    // This is the SAME VM factory used by AutoCaptureScreen — no second copy of
                    // mutable state is created; the config is read-only here.
                    val container = LocalAppContainer.current
                    val consentVm = remember {
                        val service = container.captureService
                        AutoCaptureViewModel(
                            configRepo = container.captureConfigRepository,
                            senderRepo = container.senderRepository,
                            accountRepo = container.accountRepository,
                            hasSmsPermission = { service.hasSmsPermission() },
                            requestSmsPermission = { service.requestSmsPermission() },
                            // M3-int Fix 4: route backfill through coordinator.
                            runBackfill = { nowMs -> container.captureCoordinator.runInitialBackfill(nowMs) },
                            loadApiKey = { key -> container.secureStorage.loadString(key) },
                            storeApiKey = { key, value -> container.secureStorage.storeString(key, value) },
                            clearApiKey = { key -> container.secureStorage.storeString(key, "") },
                            router = container.llmRouter,
                            // M3-int Fix 3: master toggle gates the coordinator.
                            onStartCapture = { container.startCapture() },
                            onStopCapture = { container.stopCapture() },
                        )
                    }
                    val cfg by consentVm.config.collectAsState()
                    val providerName = cfg?.cloudProvider?.name?.lowercase()
                        ?.replaceFirstChar { it.uppercase() }
                        ?: CloudProvider.CLAUDE.name.lowercase().replaceFirstChar { it.uppercase() }
                    val consentGranted = cfg?.cloudConsentAt != null
                    CloudConsentScreen(
                        providerName = providerName,
                        consentGranted = consentGranted,
                        onGrant = { consentVm.recordConsent() },
                        onRevoke = { consentVm.revokeConsent() },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("review") {
                    ReviewInboxScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { candidateId ->
                            navController.navigate("entry?candidateId=$candidateId")
                        },
                    )
                }
                // Single composable handles both "entry" (no candidateId) and
                // "entry?candidateId={candidateId}" via a nullable argument with a default of null.
                composable(
                    route = "entry?candidateId={candidateId}",
                    arguments = listOf(navArgument("candidateId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }),
                ) { entry ->
                    val cId = entry.arguments?.getString("candidateId")
                    EntryScreen(candidateId = cId, onDone = { navController.popBackStack() })
                }
                composable("txn/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    TransactionDetailScreen(txnId = id, onDone = { navController.popBackStack() })
                }
            }
            // Mount the auto-post snackbar host once; it collects captureEvents and shows Undo snackbars.
            AutoPostSnackbarHost(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}

private fun MainTab.label(): String = when (this) {
    MainTab.TODAY -> "Today"
    MainTab.MONTH -> "Month"
    MainTab.PEOPLE -> "People"
    MainTab.SETTINGS -> "Settings"
}

private fun MainTab.iconChar(): String = when (this) {
    MainTab.TODAY -> "•"
    MainTab.MONTH -> "☷"
    MainTab.PEOPLE -> "○"
    MainTab.SETTINGS -> "⚙"
}
