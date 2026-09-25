package com.n8n.mobile.studio.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
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
import androidx.compose.ui.text.font.FontWeight
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
import java.net.HttpURLConnection
import java.net.URL

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun N8nScreen(nav:NavController){
    val ctx = LocalContext.current
    val mgr = remember { LocalRuntimeManager(ctx) }
    val state by mgr.n8n.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("http://127.0.0.1:${AppConfig.N8N_PORT}") }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    fun isUp():Boolean = try{ val c=URL(url).openConnection() as HttpURLConnection; c.connectTimeout=1500; c.connect(); (c.responseCode==200).also{c.disconnect()}}catch(_:Exception){false}

    Column(modifier=Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
        RuntimeStatus("n8n", state, AppConfig.N8N_PORT, url)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            BrutalistButton(
                "Start",
                onClick = {
                    scope.launch {
                        loading = true
                        msg = "Installing..."
                        val r = mgr.startN8n()
                        msg = if (r.isSuccess) {
                            "Running"
                        } else {
                            "Error: ${r.exceptionOrNull()?.message}"
                        }
                        loading = false
                    }
                },
                modifier = Modifier.weight(1f)
            )
            BrutalistButton("Stop", onClick={ scope.launch{ mgr.stopN8n(); msg="Stopped"}}, modifier=Modifier.weight(1f), primary=false)
            BrutalistButton("Restart", onClick={ scope.launch{ mgr.restartN8n()}}, modifier=Modifier.weight(1f), primary=false)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            BrutalistOutlineButton("↗ Browser", onClick={ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}) }, modifier=Modifier.weight(1f))
            BrutalistOutlineButton("Reload", onClick={ url="http://127.0.0.1:${AppConfig.N8N_PORT}?t=${System.currentTimeMillis()}" }, modifier=Modifier.weight(1f))
        }
        if(msg.isNotEmpty()) Text(msg, fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFF94A3B8))
        if(loading) LinearProgressIndicator(modifier=Modifier.fillMaxWidth(), color=Color(0xFF22C55E))
        Box(modifier=Modifier.fillMaxSize().background(Color.White)){
            AndroidView(factory={ c ->
                WebView(c).apply{
                    settings.javaScriptEnabled=true
                    settings.domStorageEnabled=true
                    settings.allowFileAccess=true
                    webViewClient=WebViewClient()
                    webChromeClient=WebChromeClient()
                    loadUrl(url)
                }
            }, update={ it.loadUrl(url) }, modifier=Modifier.fillMaxSize())
        }
    }
}
