package com.scrollpause.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scrollpause.app.ui.diagnostics.DiagnosticsScreen
import com.scrollpause.app.ui.history.HistoryScreen
import com.scrollpause.app.ui.history.HistoryViewModelFactory
import com.scrollpause.app.ui.home.HomeScreen
import com.scrollpause.app.ui.home.HomeViewModelFactory
import com.scrollpause.app.ui.navigation.BottomNavItem
import com.scrollpause.app.ui.navigation.Screen
import com.scrollpause.app.ui.onboarding.OnboardingScreen
import com.scrollpause.app.ui.onboarding.OnboardingViewModelFactory
import com.scrollpause.app.ui.settings.SettingsScreen
import com.scrollpause.app.ui.settings.SettingsViewModelFactory
import com.scrollpause.app.ui.theme.ScrollPauseTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ScrollPauseApplication).container

        setContent {
            ScrollPauseTheme {
                ScrollPauseApp(container = container)
            }
        }
    }
}

@Composable
private fun ScrollPauseApp(container: AppContainer) {
    val navController = rememberNavController()

    // Determine start destination based on whether onboarding is complete
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val prefs = container.preferencesRepository.preferences.first()
        startDestination = if (prefs.onboardingCompleted) Screen.Home.route
        else Screen.Onboarding.route
    }

    if (startDestination == null) return  // brief loading — invisible to user

    val showBottomBar = currentRoute(navController) in listOf(
        Screen.Home.route, Screen.History.route, Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(visible = showBottomBar) {
                ScrollPauseBottomBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    viewModel = viewModel(
                        factory = OnboardingViewModelFactory(container.preferencesRepository)
                    ),
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel(
                        factory = HomeViewModelFactory(
                            container.reelRepository,
                            container.preferencesRepository,
                        )
                    ),
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModel(
                        factory = HistoryViewModelFactory(container.reelRepository)
                    ),
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel(
                        factory = SettingsViewModelFactory(
                            container.reelRepository,
                            container.preferencesRepository,
                        )
                    ),
                )
            }
            if (BuildConfig.DEBUG) {
                composable(Screen.Diagnostics.route) {
                    DiagnosticsScreen()
                }
            }
        }
    }
}

@Composable
private fun ScrollPauseBottomBar(navController: NavHostController) {
    val currentRoute = currentRoute(navController)

    NavigationBar {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun currentRoute(navController: NavHostController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}
