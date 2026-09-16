package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.n8n.mobile.studio.runtime.RuntimeState

@Composable
fun RuntimeStatus(name:String, state:RuntimeState, port:Int, url:String?=null){
    val (dot, label, dotColor) = when(state){
        RuntimeState.RUNNING -> Triple("●","RUNNING", Color(0xFF22C55E))
        RuntimeState.INSTALLING -> Triple("◐","INSTALLING", Color(0xFFF59E0B))
        RuntimeState.STARTING -> Triple("◐","STARTING", Color(0xFFF59E0B))
        RuntimeState.ERROR -> Triple("●","ERROR", Color(0xFFEF4444))
        RuntimeState.STOPPED, RuntimeState.IDLE -> Triple("○","STOPPED", Color(0xFF64748B))
        else -> Triple("○", state.name, Color(0xFF94A3B8))
    }
    Row(modifier=Modifier.fillMaxWidth().border(2.dp, Color(0xFF334155)).background(Color(0xFF0F172A)).padding(12.dp), verticalAlignment=Alignment.CenterVertically){
        Text(dot, color=dotColor, fontSize=14.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)){
            Text(name, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=13.sp, color=Color.White)
            Text("$label  •  :$port" + (if(url!=null && state==RuntimeState.RUNNING) "  •  $url" else ""), fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFF94A3B8))
        }
    }
}

@Composable
fun LogConsole(logs:String, modifier:Modifier=Modifier){
    Box(modifier=modifier.background(Color(0xFF0F172A)).border(2.dp, Color(0xFF334155)).padding(10.dp)){
        Text(logs.ifEmpty{"No logs yet — tap Start to bootstrap Node + n8n."}, fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFF22C55E), lineHeight=14.sp)
    }
}
