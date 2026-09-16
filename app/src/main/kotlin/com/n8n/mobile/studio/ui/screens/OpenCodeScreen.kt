package com.n8n.mobile.studio.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.runtime.LocalRuntimeManager
import com.n8n.mobile.studio.ui.components.BrutalistButton
import com.n8n.mobile.studio.ui.components.BrutalistOutlineButton
import com.n8n.mobile.studio.ui.components.RuntimeStatus
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenCodeScreen(nav:NavController){
    val ctx = LocalContext.current
    val mgr = remember { LocalRuntimeManager(ctx) }
    val state by mgr.openCode.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("http://127.0.0.1:${AppConfig.OPENCODE_PORT}") }
    Column(modifier=Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
        RuntimeStatus("OpenCode", state, AppConfig.OPENCODE_PORT, url)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            BrutalistButton("Start", onClick={ scope.launch{ mgr.startOpenCode()}}, modifier=Modifier.weight(1f))
            BrutalistButton("Stop", onClick={ scope.launch{ mgr.stopOpenCode()}}, modifier=Modifier.weight(1f), primary=false)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            BrutalistOutlineButton("↗ Browser", onClick={ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}) }, modifier=Modifier.weight(1f))
            BrutalistOutlineButton("Reload", onClick={ url="http://127.0.0.1:${AppConfig.OPENCODE_PORT}?t=${System.currentTimeMillis()}" }, modifier=Modifier.weight(1f))
        }
        Text("GUI + Terminal share same app filesystem: /data/data/com.n8n.mobile.studio/files", fontFamily=FontFamily.Monospace, fontSize=10.sp, color=Color(0xFF94A3B8))
        Box(modifier=Modifier.fillMaxSize().background(Color.White)){
            AndroidView(factory={ c -> WebView(c).apply{ settings.javaScriptEnabled=true; settings.domStorageEnabled=true; webViewClient=WebViewClient(); loadUrl(url) }}, update={ it.loadUrl(url)}, modifier=Modifier.fillMaxSize())
        }
    }
}
