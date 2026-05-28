package app.hisaab.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.screens.entry.EntryScreen
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
        NavHost(
            navController = navController,
            startDestination = MainTab.TODAY.name,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(MainTab.TODAY.name) {
                TodayScreen(onTxnClick = { id -> navController.navigate("txn/$id") })
            }
            composable(MainTab.MONTH.name) {
                PlaceholderScreen("Month — coming in P0c-3")
            }
            composable(MainTab.PEOPLE.name) {
                PlaceholderScreen("People — coming in P0c-3")
            }
            composable(MainTab.SETTINGS.name) {
                PlaceholderScreen("Settings — coming in P0c-3")
            }
            composable("entry") {
                EntryScreen(onDone = { navController.popBackStack() })
            }
            composable("txn/{id}") { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                TransactionDetailScreen(txnId = id, onDone = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(text: String) {
    val palette = LocalHisaabPalette.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = palette.muted)
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
