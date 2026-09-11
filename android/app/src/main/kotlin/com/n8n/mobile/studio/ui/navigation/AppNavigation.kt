package com.n8n.mobile.studio.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.n8n.mobile.studio.ui.screens.DashboardScreen
import com.n8n.mobile.studio.ui.screens.EditorScreen
import com.n8n.mobile.studio.ui.screens.ExecutionsScreen
import com.n8n.mobile.studio.ui.screens.InstancesScreen
import com.n8n.mobile.studio.ui.screens.SettingsScreen
import com.n8n.mobile.studio.ui.screens.WorkflowsScreen

private data class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val items = listOf(
        Destination("dashboard", "Dashboard", Icons.Default.Dashboard),
        Destination("workflows", "Workflows", Icons.Default.AccountTree),
        Destination("executions", "Executions", Icons.Default.PlayArrow),
        Destination("ai", "AI", Icons.Default.AutoAwesome),
        Destination("settings", "Settings", Icons.Default.Settings),
    )
    Scaffold(bottomBar = {
        NavigationBar {
            items.forEach { item ->
                NavigationBarItem(
                    selected = false,
                    onClick = { nav.navigate(item.route) { launchSingleTop = true } },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { androidx.compose.material3.Text(item.label) }
                )
            }
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = "dashboard", androidx.compose.ui.Modifier.padding(padding)) {
            composable("dashboard") { DashboardScreen(onInstances = { nav.navigate("instances") }) }
            composable("instances") { InstancesScreen() }
            composable("workflows") { WorkflowsScreen(onEditor = { nav.navigate("editor") }) }
            composable("editor") { EditorScreen(onBack = { nav.popBackStack() }) }
            composable("executions") { ExecutionsScreen() }
            composable("ai") { com.n8n.mobile.studio.ui.screens.AiScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
