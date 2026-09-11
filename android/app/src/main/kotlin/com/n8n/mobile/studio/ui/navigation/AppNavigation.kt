package com.n8n.mobile.studio.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.n8n.mobile.studio.ui.theme.StudioPalette
import com.n8n.mobile.studio.ui.screens.*

private data class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val items = listOf(
        Destination(Routes.DASHBOARD, "HOME", Icons.Default.Dashboard),
        Destination(Routes.WORKFLOWS, "FLOWS", Icons.Default.AccountTree),
        Destination(Routes.EXECUTIONS, "RUNS", Icons.Default.PlayArrow),
        Destination(Routes.HUB, "LOCAL", Icons.Default.Hub),
        Destination(Routes.SETTINGS, "SET", Icons.Default.Settings),
    )

    Scaffold(
        containerColor = StudioPalette.Background,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioPalette.Background)
                    .border(2.dp, StudioPalette.Primary)
                    .navigationBarsPadding()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .weight(1f)
                            .border(2.dp, StudioPalette.Primary)
                            .then(
                                if (selected) Modifier.background(StudioPalette.Primary)
                                else Modifier.background(Color.Transparent)
                            ),
                        color = if (selected) StudioPalette.Primary else StudioPalette.White,
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { nav.navigate(item.route) { launchSingleTop = true; restoreState = true } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(item.icon, contentDescription = item.label, tint = if (selected) StudioPalette.White else StudioPalette.Primary)
                            Text(" ${item.label}", color = if (selected) StudioPalette.White else StudioPalette.Primary)
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.DASHBOARD, Modifier.padding(padding)) {
            composable(Routes.DASHBOARD) { DashboardScreen(onInstances = { nav.navigate(Routes.INSTANCES) }, onAi = { nav.navigate(Routes.AI) }) }
            composable(Routes.INSTANCES) { InstancesScreen() }
            composable(Routes.WORKFLOWS) { WorkflowsScreen(onEditor = { nav.navigate(Routes.EDITOR) }) }
            composable(Routes.EDITOR) { EditorScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.EXECUTIONS) { ExecutionsScreen() }
            composable(Routes.AI) { AiScreen() }
            composable(Routes.HUB) { LocalHubScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
