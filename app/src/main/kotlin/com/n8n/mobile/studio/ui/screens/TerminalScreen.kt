package com.n8n.mobile.studio.ui.screens

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
import androidx.navigation.NavController
import com.n8n.mobile.studio.terminal.TerminalCommand
import com.n8n.mobile.studio.terminal.TerminalEngine
import com.n8n.mobile.studio.terminal.TerminalEnvironment
import com.n8n.mobile.studio.runtime.RuntimePaths
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(nav:NavController){
    val ctx = LocalContext.current
    val engine = remember { TerminalEngine(ctx) }
    val session = remember { engine.createSession(TerminalEnvironment(workingDirectory = RuntimePaths(ctx).base.absolutePath)) }
    var input by remember { mutableStateOf("node --version && npm --version && ls -la") }
    var output by remember { mutableStateOf("Terminal ready — same filesystem as n8n/OpenCode.\nTry: node --version, npm --version, n8n --version, ls ~/n8n\n") }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    Column(modifier=Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("TERMINAL  •  app-owned filesystem", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=11.sp, color=Color.White, letterSpacing=1.sp)
        Box(modifier=Modifier.weight(1f).background(Color.Black).padding(10.dp)){
            Text(output, fontFamily=FontFamily.Monospace, fontSize=12.sp, color=Color(0xFF22C55E), modifier=Modifier.verticalScroll(scroll))
        }
        OutlinedTextField(value=input, onValueChange={input=it}, modifier=Modifier.fillMaxWidth(), placeholder={ Text("command", fontFamily=FontFamily.Monospace, fontSize=12.sp)}, textStyle= androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Monospace, fontSize=12.sp, color=Color.White), colors= OutlinedTextFieldDefaults.colors(focusedBorderColor=Color(0xFF334155), unfocusedBorderColor=Color(0xFF334155)))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
            Button(onClick={
                scope.launch{
                    output += "\n\$ $input\n"
                    val res = engine.execute(session, TerminalCommand(command="sh", args=listOf("-c", input), environment=session.env))
                    output += res.output + "\n"
                }
            }, modifier=Modifier.weight(1f), shape= androidx.compose.foundation.shape.RoundedCornerShape(0.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFF8FAFC), contentColor=Color(0xFF0F172A))){ Text("Run", fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold)}
            OutlinedButton(onClick={ output="" }, modifier=Modifier.weight(1f), shape= androidx.compose.foundation.shape.RoundedCornerShape(0.dp)){ Text("Clear", fontFamily=FontFamily.Monospace)}
        }
    }
}
