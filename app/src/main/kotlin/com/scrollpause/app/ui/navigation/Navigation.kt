package com.scrollpause.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Type-safe route definitions for the Navigation component. */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object Diagnostics : Screen("diagnostics") // debug only
}

/** Items shown in the bottom navigation bar. */
enum class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
) {
    HOME(Screen.Home, Icons.Default.Home, "Home"),
    HISTORY(Screen.History, Icons.Default.BarChart, "History"),
    SETTINGS(Screen.Settings, Icons.Default.Settings, "Settings"),
}
