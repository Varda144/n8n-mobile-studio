package com.n8n.mobile.studio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.runtime.LocalRuntimeManager
import com.n8n.mobile.studio.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(nav: NavController){
    val ctx = LocalContext.current
    val mgr = remember { LocalRuntimeManager(ctx) }
    val n8nState by mgr.n8n.state.collectAsStateWithLifecycle()
    val ocState by mgr.openCode.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf("n8n Mobile Studio v${AppConfig.N8N_VERSION}\nNode ${AppConfig.NODE_VERSION} • Port ${AppConfig.N8N_PORT} / ${AppConfig.OPENCODE_PORT}\n\nTap Start to bootstrap Termux Node (aarch64) + npm install n8n + OpenCode.\nFirst run downloads ~35MB and may take 3-6 min.\n") }
    var busy by remember { mutableStateOf(false) }

    Column(modifier=Modifier.fillMaxSize().background(Color(0xFF0F172A)).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
        // header brutalist
        BrutalistCard{
            Text("n8n Mobile Studio", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Black, fontSize=18.sp, color=Color.White, letterSpacing=1.sp)
            Spacer(Modifier.height(4.dp))
            Text("Local runtimes • No Termux • No cloud", fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFF94A3B8))
            Spacer(Modifier.height(4.dp))
            Text("TWO RUNTIMES  —  n8n (5678)  •  OpenCode (8080)  —  shared app filesystem", fontFamily=FontFamily.Monospace, fontSize=10.sp, color=Color(0xFF22C55E))
        }

        RuntimeStatus("n8n", n8nState, AppConfig.N8N_PORT, "http://127.0.0.1:${AppConfig.N8N_PORT}")
        RuntimeStatus("OpenCode", ocState, AppConfig.OPENCODE_PORT, "http://127.0.0.1:${AppConfig.OPENCODE_PORT}")

        // controls 2x2
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            BrutalistButton("START n8n", onClick={
                if(busy) return@BrutalistButton
                busy=true; logs+="\n[•] Installing Node (Termux aarch64) if needed...\n"
                scope.launch{
                    val r = mgr.startN8n()
                    logs += if(r.isSuccess) "\n[✓] n8n RUNNING → http://127.0.0.1:${AppConfig.N8N_PORT}\n" else "\n[✗] n8n failed: ${r.exceptionOrNull()?.message}\n"
                    busy=false
                }
            }, modifier=Modifier.weight(1f), enabled=!busy)
            BrutalistButton("STOP", onClick={ scope.launch{ mgr.stopN8n(); logs+="\n[■] n8n stopped\n"} }, modifier=Modifier.weight(1f), primary=false, enabled=!busy)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            BrutalistButton("START OpenCode", onClick={
                if(busy) return@BrutalistButton
                busy=true; logs+="\n[•] Starting OpenCode...\n"
                scope.launch{
                    val r = mgr.startOpenCode()
                    logs += if(r.isSuccess) "\n[✓] OpenCode RUNNING → http://127.0.0.1:${AppConfig.OPENCODE_PORT}\n" else "\n[✗] OpenCode failed: ${r.exceptionOrNull()?.message}\n"
                    busy=false
                }
            }, modifier=Modifier.weight(1f), enabled=!busy)
            BrutalistButton("STOP", onClick={ scope.launch{ mgr.stopOpenCode(); logs+="\n[■] OpenCode stopped\n"} }, modifier=Modifier.weight(1f), primary=false, enabled=!busy)
        }

        // quick open buttons
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            BrutalistOutlineButton("Open n8n →", onClick={ nav.navigate("n8n") }, modifier=Modifier.weight(1f))
            BrutalistOutlineButton("Open OpenCode →", onClick={ nav.navigate("opencode") }, modifier=Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            BrutalistOutlineButton("Terminal", onClick={ nav.navigate("terminal") }, modifier=Modifier.weight(1f))
            BrutalistOutlineButton("External browser", onClick={
                val url = "http://127.0.0.1:${AppConfig.N8N_PORT}"
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)})
            }, modifier=Modifier.weight(1f))
        }

        // logs
        Text("LOGS", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=11.sp, color=Color(0xFFF8FAFC), letterSpacing=1.sp)
        LogConsole(logs, modifier=Modifier.fillMaxWidth().height(220.dp))

        // info card
        BrutalistCard {
            Text("How it works (real local install)", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=12.sp, color=Color.White)
            Spacer(Modifier.height(6.dp))
            Text("1. App fetches Termux Packages for your ABI and downloads .debs (nodejs, libuv, openssl…)\n2. Extracts via Ar+XZ directly to app's usr/ (no root)\n3. Runs npm install n8n@${AppConfig.N8N_VERSION} and opencode\n4. Starts n8n on :5678 and OpenCode stub on :8080 using app-owned filesystem\n5. WebView and external browser share same instance", fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFF94A3B8), lineHeight=14.sp)
        }

        Spacer(Modifier.height(80.dp))
    }
}
