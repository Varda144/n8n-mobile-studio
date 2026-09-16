package com.n8n.mobile.studio.ui.navigation
sealed class Routes(val route:String){
    object Dashboard:Routes("dashboard")
    object N8n:Routes("n8n")
    object OpenCode:Routes("opencode")
    object Terminal:Routes("terminal")
    object Settings:Routes("settings")
}
