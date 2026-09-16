package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.ui.components.BrutalistCard

@Composable
fun SettingsScreen(nav:NavController){
    val ctx = LocalContext.current
    val paths = remember { RuntimePaths(ctx) }
    Column(modifier=Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
        BrutalistCard{
            Text("n8n Mobile Studio", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Black, fontSize=16.sp, color=Color.White)
            Spacer(Modifier.height(6.dp))
            Text("Version 1.2.0  •  n8n ${AppConfig.N8N_VERSION}  •  Node ${AppConfig.NODE_VERSION}", fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFF94A3B8))
            Spacer(Modifier.height(4.dp))
            Text("App filesystem: ${paths.base.absolutePath}", fontFamily=FontFamily.Monospace, fontSize=10.sp, color=Color(0xFF64748B))
        }
        BrutalistCard{
            Text("Pinned versions — reproducible builds", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=12.sp, color=Color.White)
            Spacer(Modifier.height(6.dp))
            Text("• Node ${AppConfig.NODE_VERSION} from Termux apt (aarch64/arm/x86_64)\n• n8n ${AppConfig.N8N_VERSION} via npm\n• OpenCode stub server on :8080\n• No Termux app required\n• No external server — all local", fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFF94A3B8), lineHeight=14.sp)
        }
        BrutalistCard{
            Text("Permissions", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=12.sp, color=Color.White)
            Text("INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS", fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFF94A3B8))
        }
    }
}
