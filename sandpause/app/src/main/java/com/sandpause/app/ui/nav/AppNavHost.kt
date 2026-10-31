package com.sandpause.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sandpause.app.ui.parent.AppsScreen
import com.sandpause.app.ui.parent.DeviceOwnerScreen
import com.sandpause.app.ui.parent.GateScreen
import com.sandpause.app.ui.parent.HomeScreen
import com.sandpause.app.ui.parent.ParentStart
import com.sandpause.app.ui.parent.ParentViewModel
import com.sandpause.app.ui.parent.PermissionsScreen
import com.sandpause.app.ui.parent.RecoveryScreen
import com.sandpause.app.ui.parent.ReportsScreen
import com.sandpause.app.ui.parent.Routes
import com.sandpause.app.ui.parent.SchedulesScreen
import com.sandpause.app.ui.parent.SetupScreen

/** The parent area. It starts at setup, the PIN gate or home, depending on the stored state. */
@Composable
fun AppNavHost(viewModel: ParentViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val start by viewModel.start.collectAsState()
    val startRoute = when (start) {
        null -> null
        ParentStart.SETUP -> Routes.SETUP
        ParentStart.GATE -> Routes.GATE
        ParentStart.HOME -> Routes.HOME
    }
    if (startRoute == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.SETUP) {
            SetupScreen(viewModel = viewModel) {
                navController.navigate(Routes.PERMISSIONS) {
                    popUpTo(Routes.SETUP) { inclusive = true }
                }
            }
        }
        composable(Routes.GATE) {
            GateScreen(
                viewModel = viewModel,
                onUnlocked = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.GATE) { inclusive = true } }
                },
                onForgot = { navController.navigate(Routes.RECOVERY) },
            )
        }
        composable(Routes.RECOVERY) {
            RecoveryScreen(viewModel = viewModel) {
                navController.navigate(Routes.HOME) { popUpTo(Routes.GATE) { inclusive = true } }
            }
        }
        composable(Routes.HOME) {
            HomeScreen(viewModel = viewModel) { route -> navController.navigate(route) }
        }
        composable(Routes.SCHEDULES) { SchedulesScreen(viewModel = viewModel) }
        composable(Routes.APPS) { AppsScreen(viewModel = viewModel) }
        composable(Routes.REPORTS) { ReportsScreen(viewModel = viewModel) }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onDone = {
                if (!navController.popBackStack()) {
                    navController.navigate(Routes.HOME)
                }
            })
        }
        composable(Routes.OWNER) { DeviceOwnerScreen(viewModel = viewModel) }
    }
}
