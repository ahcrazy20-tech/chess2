package com.chesshelper.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chesshelper.app.data.EngineViewModel

/**
 * ContentView — root Tab scaffold, port of Sources/ContentView.swift
 *
 * 6 tabs: Play, Review, Openings, Puzzles, Stats, Settings  (same ordering as iOS)
 * On S26 Ultra 6.9" we use NavigationBar (phone) but could adapt to NavigationRail on tablets/DeX.
 */
data class TabItem(val label: String, val icon: ImageVector, val index: Int)

@Composable
fun ChessHelperApp(
    engine: EngineViewModel = viewModel(),
    router: AppRouter = viewModel()
) {
    val context = LocalContext.current
    val stealth: StealthManager = viewModel(
        factory = viewModelFactory {
            initializer { StealthManager(context.applicationContext) }
        }
    )

    // Ensure stealth cleans up when composition leaves (sensor unregister handled in ViewModel onCleared alternative,
    // but since StealthManager is not a ViewModel anymore we handle via DisposableEffect in Settings if needed.
    // For now StealthManager is lifecycle-aware via manual unregister; we keep it simple.)
    val tabs = listOf(
        TabItem("Play", Icons.Filled.SportsEsports, 0),
        TabItem("Review", Icons.Filled.Search, 1),
        TabItem("Openings", Icons.Filled.MenuBook, 2),
        TabItem("Puzzles", Icons.Filled.Extension, 3),
        TabItem("Stats", Icons.Filled.BarChart, 4),
        TabItem("Settings", Icons.Filled.Settings, 5),
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = router.selectedTab == tab.index,
                        onClick = { router.selectTab(tab.index) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (router.selectedTab) {
                0 -> PlayView(engine = engine, router = router, stealth = stealth)
                1 -> ReviewView(router = router)
                2 -> OpeningsView(router = router)
                3 -> PuzzlesView(router = router)
                4 -> StatsView(router = router)
                5 -> SettingsView(stealth = stealth)
            }
        }
    }
}
