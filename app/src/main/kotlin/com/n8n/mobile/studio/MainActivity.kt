package com.n8n.mobile.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.n8n.mobile.studio.ui.screens.*
import com.n8n.mobile.studio.ui.theme.N8nMobileStudioTheme
import com.n8n.mobile.studio.ui.navigation.Routes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            N8nMobileStudioTheme {
                val nav = rememberNavController()
                val back by nav.currentBackStackEntryAsState()
                val route = back?.destination?.route
                Scaffold(
                    topBar = {
                        Row(modifier=Modifier.fillMaxWidth().background(Color(0xFF0F172A)).border(2.dp, Color(0xFF334155)).padding(horizontal=12.dp, vertical=10.dp), horizontalArrangement=Arrangement.SpaceBetween){
                            Text("⬢  n8n  •  OpenCode", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Black, fontSize=12.sp, color=Color.White, letterSpacing=1.sp)
                            Text("LOCAL  •  OFFLINE-CAPABLE", fontFamily=FontFamily.Monospace, fontSize=9.sp, color=Color(0xFF22C55E))
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor=Color(0xFF0F172A), tonalElevation=0.dp, modifier=Modifier.border(2.dp, Color(0xFF334155))){
                            val items = listOf(
                                Routes.Dashboard to "DASH",
                                Routes.N8n to "n8n",
                                Routes.OpenCode to "CODE",
                                Routes.Terminal to "TERM",
                                Routes.Settings to "SET"
                            )
                            items.forEach{ (r,label) ->
                                NavigationBarItem(
                                    selected = route==r.route,
                                    onClick = { if(route!=r.route) nav.navigate(r.route){ launchSingleTop=true } },
                                    label = { Text(label, fontFamily=FontFamily.Monospace, fontSize=10.sp, fontWeight=FontWeight.Bold) },
                                    icon = { Text(if(route==r.route)"●" else "○", fontSize=10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor=Color(0xFF22C55E),
                                        selectedTextColor=Color.White,
                                        indicatorColor=Color(0xFF1B2336),
                                        unselectedIconColor=Color(0xFF64748B),
                                        unselectedTextColor=Color(0xFF64748B)
                                    )
                                )
                            }
                        }
                    },
                    containerColor = Color(0xFF0F172A)
                ){ pad ->
                    Box(Modifier.padding(pad)){
                        NavHost(navController=nav, startDestination=Routes.Dashboard.route){
                            composable(Routes.Dashboard.route){ DashboardScreen(nav) }
                            composable(Routes.N8n.route){ N8nScreen(nav) }
                            composable(Routes.OpenCode.route){ OpenCodeScreen(nav) }
                            composable(Routes.Terminal.route){ TerminalScreen(nav) }
                            composable(Routes.Settings.route){ SettingsScreen(nav) }
                        }
                    }
                }
            }
        }
    }
}
