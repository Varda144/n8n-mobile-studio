package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onInstances: () -> Unit, onAi: () -> Unit) {
    val metrics = listOf("Workflows" to "—", "Active" to "—", "Successful runs" to "—", "Failed runs" to "—")
    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("N8N Mobile Studio", style = MaterialTheme.typography.titleLarge)
                Text("Local-first automation workspace", style = MaterialTheme.typography.labelSmall)
            }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(onClick = onInstances, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.CloudDone, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("n8n connection", style = MaterialTheme.typography.titleMedium)
                        Text("No active instance. Add a local or self-hosted endpoint.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onAi() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Text("  AI Builder")
                }
                OutlinedButton(onClick = onInstances, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.Dns, contentDescription = null)
                    Text("  Instances")
                }
            }

            Text("Overview", style = MaterialTheme.typography.titleMedium)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(metrics) { (label, value) ->
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(value, style = MaterialTheme.typography.headlineMedium)
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("System status", style = MaterialTheme.typography.titleMedium)
                    StatusLine(Icons.Default.Dns, "n8n API", "Not configured")
                    StatusLine(Icons.Default.Hub, "Local tool bridge", "Available when a local bridge is running")
                    StatusLine(Icons.Default.PlayCircle, "Background sync", "Ready")
                    StatusLine(Icons.Default.ErrorOutline, "Secrets", "Keystore-backed")
                }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}
