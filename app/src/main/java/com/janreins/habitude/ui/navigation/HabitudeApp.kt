package com.janreins.habitude.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.ui.detail.HabitDetailScreen
import com.janreins.habitude.ui.edit.EditHabitScreen
import com.janreins.habitude.ui.edit.EditHabitViewModel
import com.janreins.habitude.ui.screens.ProgressScreen
import com.janreins.habitude.ui.screens.SettingsScreen
import com.janreins.habitude.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Icons.Outlined.WbSunny),
    Progress("progress", "Progress", Icons.Outlined.Insights),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

private val EDIT_ROUTE =
    "habit?${EditHabitViewModel.ARG_ID}={${EditHabitViewModel.ARG_ID}}&${EditHabitViewModel.ARG_TYPE}={${EditHabitViewModel.ARG_TYPE}}"

private fun editRoute(id: Long = 0, type: HabitType = HabitType.BUILD) =
    "habit?${EditHabitViewModel.ARG_ID}=$id&${EditHabitViewModel.ARG_TYPE}=${type.name}"

private const val DETAIL_ROUTE = "detail/{id}"

private fun detailRoute(id: Long) = "detail/$id"

/*
 * Screens only move on while they're the one showing. A quick double tap on Back would otherwise
 * pop twice and leave an empty screen, and a double tap on a habit would open it twice.
 */
private fun NavController.isShowing(from: NavBackStackEntry) = currentBackStackEntry?.id == from.id

private fun NavController.backFrom(from: NavBackStackEntry) {
    if (isShowing(from)) popBackStack()
}

private fun NavController.openFrom(from: NavBackStackEntry, route: String) {
    if (isShowing(from)) navigate(route)
}

@Composable
fun HabitudeApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showTabs = currentDestination == null || Tab.entries.any { it.route == currentDestination.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showTabs) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Today.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.Today.route) { entry ->
                TodayScreen(
                    onAddHabit = { type -> navController.openFrom(entry, editRoute(type = type)) },
                    onOpenHabit = { id -> navController.openFrom(entry, detailRoute(id)) },
                )
            }
            composable(Tab.Progress.route) { entry ->
                ProgressScreen(onOpenHabit = { id -> navController.openFrom(entry, detailRoute(id)) })
            }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(
                route = EDIT_ROUTE,
                arguments = listOf(
                    navArgument(EditHabitViewModel.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument(EditHabitViewModel.ARG_TYPE) {
                        type = NavType.StringType
                        defaultValue = HabitType.BUILD.name
                    },
                ),
            ) { entry ->
                EditHabitScreen(
                    onDone = { navController.backFrom(entry) },
                    onDeleted = {
                        // Skip past the deleted habit's stats page too.
                        if (navController.isShowing(entry) && !navController.popBackStack(DETAIL_ROUTE, inclusive = true)) {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                HabitDetailScreen(
                    habitId = id,
                    onBack = { navController.backFrom(entry) },
                    onEdit = { navController.openFrom(entry, editRoute(id = id)) },
                )
            }
        }
    }
}
