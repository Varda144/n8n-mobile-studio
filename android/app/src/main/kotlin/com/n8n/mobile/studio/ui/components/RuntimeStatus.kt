package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.ui.label
import com.n8n.mobile.studio.ui.theme.StudioPalette

/**
 * Status card for one runtime, shared by the hub and the dashboard so both always
 * show identical facts: state, endpoint, pid, version, payload and last message.
 */
@Composable
fun RuntimeStatus(status: EmbeddedComponentStatus, modifier: Modifier = Modifier) {
    BrutalistSurface(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(componentName(status.component), fontWeight = FontWeight.Black)
                Text(
                    status.state.label(),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = stateColor(status.state),
                    modifier = Modifier
                        .background(stateBackground(status.state))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            status.version?.let { Detail("VERSION", it) }
            status.endpoint?.let { Detail("ENDPOINT", it) }
            status.pid?.let { Detail("PID", it.toString()) }
            Detail("PAYLOAD", if (status.payloadReady) "installed" else "NOT INSTALLED")
            Detail("MESSAGE", status.message)
            status.healthDetail?.let { Detail("HEALTH", it) }
            if (status.restartCount > 0) Detail("RESTARTS", status.restartCount.toString())
            Text(
                "GUI and terminal attach to this same process",
                style = MaterialTheme.typography.labelSmall,
                color = StudioPalette.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = StudioPalette.Muted,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

fun componentName(component: EmbeddedComponent): String = when (component) {
    EmbeddedComponent.N8N -> "n8n"
    EmbeddedComponent.OPENCODE -> "OpenCode"
}

@Composable
fun stateColor(state: EmbeddedProcessState): Color = when (state) {
    EmbeddedProcessState.RUNNING -> StudioPalette.Primary
    EmbeddedProcessState.STARTING, EmbeddedProcessState.STOPPING -> StudioPalette.Muted
    EmbeddedProcessState.DEGRADED -> StudioPalette.Primary
    EmbeddedProcessState.STOPPED, EmbeddedProcessState.FAILED, EmbeddedProcessState.NOT_INSTALLED ->
        StudioPalette.Muted
}

@Composable
private fun stateBackground(state: EmbeddedProcessState): Color = when (state) {
    EmbeddedProcessState.RUNNING -> StudioPalette.Accent
    EmbeddedProcessState.DEGRADED -> StudioPalette.Accent
    EmbeddedProcessState.FAILED -> StudioPalette.Accent
    else -> StudioPalette.Background
}
