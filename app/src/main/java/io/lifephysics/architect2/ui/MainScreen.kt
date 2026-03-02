package io.lifephysics.architect2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.lifephysics.architect2.ui.composables.XpPopup
import io.lifephysics.architect2.ui.screens.AnalyticsScreen
import io.lifephysics.architect2.ui.screens.HistoryScreen
import io.lifephysics.architect2.ui.screens.TasksScreen
import io.lifephysics.architect2.ui.screens.TrendingScreen
import io.lifephysics.architect2.ui.viewmodel.AnalyticsViewModel
import io.lifephysics.architect2.ui.viewmodel.AnalyticsViewModelFactory
import io.lifephysics.architect2.ui.viewmodel.MainViewModel

/**
 * The root composable for the entire app UI.
 *
 * Sets up the bottom navigation bar and the [NavHost] that hosts all screens.
 * The centre tab is a shortcut that navigates to Tasks and focuses the add-task field.
 *
 * The [NavigationBar] is hidden via [AnimatedVisibility] when the IME (keyboard) is
 * visible — this is the WhatsApp-style behaviour where the nav bar disappears when
 * the user is typing, giving the input card more room and preventing inset conflicts.
 *
 * @param viewModel The [MainViewModel] shared across all screens.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    val analyticsViewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModelFactory(viewModel.repository)
    )

    // Flag that tells TasksScreen to focus the add-task field
    var focusAddTask by remember { mutableStateOf(false) }

    // IME visibility — WindowInsets.ime must be read in @Composable scope (not inside remember lambda)
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                // Hide the navigation bar when the keyboard is open (WhatsApp-style)
                AnimatedVisibility(visible = !imeVisible) {
                    NavigationBar {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        // Tasks tab
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Screen.Tasks.icon,
                                    contentDescription = Screen.Tasks.label,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = null,
                            selected = currentDestination?.hierarchy?.any { it.route == Screen.Tasks.route } == true,
                            onClick = {
                                navController.navigate(Screen.Tasks.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )

                        // History tab
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Screen.History.icon,
                                    contentDescription = Screen.History.label,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = null,
                            selected = currentDestination?.hierarchy?.any { it.route == Screen.History.route } == true,
                            onClick = {
                                navController.navigate(Screen.History.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )

                        // Centre + shortcut tab
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "New Task",
                                    modifier = Modifier.size(30.dp)
                                )
                            },
                            label = null,
                            selected = false,
                            onClick = {
                                navController.navigate(Screen.Tasks.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                focusAddTask = true
                            }
                        )

                        // Trending tab
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Screen.Trending.icon,
                                    contentDescription = Screen.Trending.label,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = null,
                            selected = currentDestination?.hierarchy?.any { it.route == Screen.Trending.route } == true,
                            onClick = {
                                navController.navigate(Screen.Trending.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )

                        // Analytics tab
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Screen.Analytics.icon,
                                    contentDescription = Screen.Analytics.label,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = null,
                            selected = currentDestination?.hierarchy?.any { it.route == Screen.Analytics.route } == true,
                            onClick = {
                                navController.navigate(Screen.Analytics.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Tasks.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Tasks.route) {
                    TasksScreen(
                        viewModel = viewModel,
                        focusAddTask = focusAddTask,
                        onFocusHandled = { focusAddTask = false }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(viewModel = viewModel)
                }
                composable(Screen.Trending.route) {
                    val trendsState by viewModel.trendsUiState.collectAsStateWithLifecycle()
                    TrendingScreen(trendsUiState = trendsState)
                }
                composable(Screen.Analytics.route) {
                    AnalyticsScreen(viewModel = analyticsViewModel)
                }
            }
        }

        if (uiState.xpPopupVisible) {
            XpPopup(
                amount = uiState.xpPopupAmount,
                isCritical = uiState.xpPopupIsCritical,
                onDismiss = { viewModel.onDismissXpPopup() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
