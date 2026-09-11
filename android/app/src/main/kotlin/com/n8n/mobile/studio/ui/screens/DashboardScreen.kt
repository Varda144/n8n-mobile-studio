package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onInstances: () -> Unit) {
    val metrics = listOf("Workflows" to "—", "Active" to "—", "Success" to "—", "Failed" to "—")
    Scaffold(topBar = { TopAppBar(title = { Text("N8N Mobile Studio") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(onClick = onInstances, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CloudDone, contentDescription = null)
                    Column { Text("Active n8n instance", style = MaterialTheme.typography.titleMedium); Text("Configure an instance to connect") }
                }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(metrics) { (label, value) ->
                    Card { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium); Text(label) } }
                }
            }
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("System status", style = MaterialTheme.typography.titleMedium)
                StatusLine(Icons.Default.Dns, "Remote API", "Not configured")
                StatusLine(Icons.Default.PlayCircle, "Background sync", "Ready")
                StatusLine(Icons.Default.ErrorOutline, "Security", "Keystore-backed secrets")
            } }
        }
    }
}

@Composable private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, contentDescription = null); Column { Text(title); Text(value, style = MaterialTheme.typography.bodySmall) } }
}
