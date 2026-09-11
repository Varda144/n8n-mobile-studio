package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.ui.theme.StudioPalette

@Composable
fun RuntimeStatus(status: EmbeddedComponentStatus, modifier: Modifier = Modifier) {
    BrutalistSurface(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(componentName(status.component), fontWeight = FontWeight.Black)
                Text(stateLabel(status.state), fontWeight = FontWeight.Bold, color = stateColor(status.state))
            }
            status.pid?.let { Text("PID $it", style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            status.endpoint?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = StudioPalette.Muted) }
            Text(status.message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun componentName(component: EmbeddedComponent): String = when (component) {
    EmbeddedComponent.N8N -> "n8n"
    EmbeddedComponent.OPENCODE -> "OpenCode"
}

private fun stateLabel(state: EmbeddedProcessState): String = when (state) {
    EmbeddedProcessState.STOPPED -> "STOPPED"
    EmbeddedProcessState.STARTING -> "STARTING"
    EmbeddedProcessState.RUNNING -> "RUNNING"
    EmbeddedProcessState.STOPPING -> "STOPPING"
    EmbeddedProcessState.FAILED -> "FAILED"
}

private fun stateColor(state: EmbeddedProcessState): androidx.compose.ui.graphics.Color = when (state) {
    EmbeddedProcessState.RUNNING -> StudioPalette.Primary
    EmbeddedProcessState.STARTING, EmbeddedProcessState.STOPPING -> StudioPalette.Muted
    EmbeddedProcessState.STOPPED, EmbeddedProcessState.FAILED -> StudioPalette.Muted
}