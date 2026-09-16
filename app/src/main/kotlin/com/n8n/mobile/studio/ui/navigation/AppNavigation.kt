package com.n8n.mobile.studio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.n8n.mobile.studio.ui.screens.*

@Composable
fun AppNavigation(){
    val nav = rememberNavController()
    NavHost(navController=nav, startDestination=Routes.Dashboard.route){
        composable(Routes.Dashboard.route){ DashboardScreen(nav) }
        composable(Routes.N8n.route){ N8nScreen(nav) }
        composable(Routes.OpenCode.route){ OpenCodeScreen(nav) }
        composable(Routes.Terminal.route){ TerminalScreen(nav) }
        composable(Routes.Settings.route){ SettingsScreen(nav) }
    }
}
