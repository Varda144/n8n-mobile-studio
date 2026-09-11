package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.ui.LocalRuntimeClient
import com.n8n.mobile.studio.ui.label
import com.n8n.mobile.studio.ui.theme.StudioPalette

@Composable
fun DashboardScreen(onInstances: () -> Unit, onAi: () -> Unit, onLocalHub: () -> Unit = {}) {
    val client = LocalRuntimeClient.current
    val statuses = client?.statuses?.collectAsState()?.value.orEmpty()
    val n8n = statuses[EmbeddedComponent.N8N] ?: EmbeddedComponentStatus(EmbeddedComponent.N8N)
    val openCode = statuses[EmbeddedComponent.OPENCODE] ?: EmbeddedComponentStatus(EmbeddedComponent.OPENCODE)
    val serviceBound = client?.connected?.collectAsState()?.value == true
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(StudioPalette.Background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("N8N MOBILE STUDIO", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                Text("LOCAL AUTOMATION / RESEARCH WORKSPACE", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = StudioPalette.Muted)
            }
        }
        item {
            BrutalistPanel {
                Text("SYSTEM / 01", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CloudDone, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("LOCAL N8N RUNTIME", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(
                            "${n8n.state.label()} · ${n8n.endpoint ?: "loopback endpoint pending"}",
                            color = StudioPalette.Muted,
                        )
                        Text(
                            if (n8n.payloadReady) {
                                "payload ${n8n.version ?: "?"} · pid ${n8n.pid ?: "-"}"
                            } else {
                                n8n.message
                            },
                            color = StudioPalette.Muted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onLocalHub, modifier = Modifier.border(2.dp, StudioPalette.Primary)) {
                    Text("OPEN LOCAL HUB", color = StudioPalette.Primary)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrutalistAction("LOCAL HUB", Icons.Default.Hub, StudioPalette.Accent, onLocalHub, Modifier.weight(1f))
                BrutalistAction("INSTANCES", Icons.Default.Dns, StudioPalette.White, onInstances, Modifier.weight(1f))
            }
        }
        item {
            Text("OVERVIEW", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPanel("WORKFLOWS", "—", Modifier.weight(1f))
                MetricPanel("ACTIVE", "—", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPanel("SUCCESS", "—", Modifier.weight(1f))
                MetricPanel("FAILED", "—", Modifier.weight(1f))
            }
        }
        item {
            BrutalistPanel {
                Text("SYSTEM STATUS", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                StatusLine(Icons.Default.Dns, "N8N RUNTIME", "${n8n.state.label()} · ${n8n.endpoint ?: "—"}")
                StatusLine(
                    Icons.Default.Hub,
                    "OPENCODE RUNTIME",
                    "${openCode.state.label()} · ${openCode.endpoint ?: "—"}",
                )
                StatusLine(
                    Icons.Default.PlayCircle,
                    "PROCESS MANAGER",
                    if (serviceBound) "FOREGROUND SERVICE BOUND" else "SERVICE NOT BOUND",
                )
            }
        }
    }
}

@Composable
private fun BrutalistPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioPalette.White)
            .border(2.dp, StudioPalette.Primary)
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun MetricPanel(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(StudioPalette.White)
            .border(2.dp, StudioPalette.Primary)
            .padding(14.dp),
    ) {
        Text(value, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = StudioPalette.Muted)
    }
}

@Composable
private fun BrutalistAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, background: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(modifier = modifier.border(2.dp, StudioPalette.Primary), color = background) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Icon(icon, contentDescription = label, tint = StudioPalette.Primary)
            Spacer(Modifier.width(8.dp))
            Text(label, color = StudioPalette.Primary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(value, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = StudioPalette.Muted)
        }
    }
}
