package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.local.LocalRuntimeProbe

private data class LocalTool(val name: String, val role: String)

@Composable
fun LocalHubScreen() {
    val tools = listOf(
        LocalTool("n8n", "Local workflow runtime / automation"),
        LocalTool("Codex", "Local coding-agent bridge"),
        LocalTool("OpenCode", "Local coding-agent bridge"),
        LocalTool("OpenClaw", "Local agent bridge"),
        LocalTool("Hermes", "Local agent bridge"),
        LocalTool("MCP", "Local tools/resources gateway"),
    )
    var status by remember { mutableStateOf("Not checked") }
    val probe = remember { LocalRuntimeProbe() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Local Studio Hub", style = MaterialTheme.typography.headlineSmall)
                Text("Phone-first, localhost-first automation", style = MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.material3.Icon(Icons.Default.Hub, contentDescription = null)
        }
        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("n8n local runtime", style = MaterialTheme.typography.titleMedium)
                Text("Default endpoint: http://127.0.0.1:5678")
                Text(status, modifier = Modifier.padding(top = 6.dp))
                Button(
                    onClick = {
                        status = "Checking..."
                        // A coroutine-backed probe can be wired to a ViewModel in the next screen pass.
                        status = "Use the instance connection test to probe the configured runtime."
                    },
                    modifier = Modifier.padding(top = 10.dp),
                ) { Text("Check local setup") }
            }
        }
        Text("Local toolchain", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tools) { tool ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.Code, contentDescription = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(tool.name, style = MaterialTheme.typography.titleSmall)
                            Text(tool.role, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
