package com.n8n.mobile.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.n8n.N8nWebLauncher
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.runtime.service.RuntimeClient
import com.n8n.mobile.studio.ui.LocalRuntimeClient
import com.n8n.mobile.studio.ui.components.BrutalistButton
import com.n8n.mobile.studio.ui.components.RuntimeStatus
import com.n8n.mobile.studio.ui.components.RuntimeWebPanel
import com.n8n.mobile.studio.ui.components.TerminalPanel
import com.n8n.mobile.studio.ui.label
import com.n8n.mobile.studio.ui.theme.StudioPalette
import kotlinx.coroutines.launch

private enum class HubTab(val label: String, val component: EmbeddedComponent?) {
    N8N("n8n", EmbeddedComponent.N8N),
    OPENCODE("OPENCODE", EmbeddedComponent.OPENCODE),
    TERMINAL("TERMINAL", null),
    LOGS("LOGS", null),
}

/**
 * The local runtime hub.
 *
 * Everything the studio embeds is controlled from here: n8n (GUI + external
 * browser), OpenCode (GUI + terminal), the shared terminal, and the raw logs of
 * both processes. The navigation of the app is unchanged — this screen upgrades
 * the existing LOCAL destination.
 */
@Composable
fun LocalHubScreen() {
    val client = LocalRuntimeClient.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(HubTab.N8N) }
    var installMessage by remember { mutableStateOf<String?>(null) }

    val statuses = client?.statuses?.collectAsState()?.value.orEmpty()
    val logs = client?.logs?.collectAsState()?.value.orEmpty()
    val n8n = statuses[EmbeddedComponent.N8N] ?: EmbeddedComponentStatus(EmbeddedComponent.N8N)
    val openCode = statuses[EmbeddedComponent.OPENCODE] ?: EmbeddedComponentStatus(EmbeddedComponent.OPENCODE)

    val payloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val component = tab.component ?: return@rememberLauncherForActivityResult
        if (uri == null || client == null) return@rememberLauncherForActivityResult
        installMessage = "Installing ${component.label} payload…"
        scope.launch {
            val result = client.installFromUri(component, uri) { progress ->
                installMessage = "Installing ${component.label}: $progress"
            }
            installMessage = result.fold(
                onSuccess = { "${component.label} payload ${it.version} installed" },
                onFailure = { "Install failed: ${it.message}" },
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text("Local Studio Hub", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${summarise(n8n)} · ${summarise(openCode)} · loopback only",
                style = MaterialTheme.typography.bodySmall,
                color = StudioPalette.Muted,
            )
            if (client == null || client.connected.collectAsState().value.not()) {
                Text(
                    "runtime service not connected — controls are disabled until it is bound",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioPalette.Muted,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HubTab.entries.forEach { entry ->
                val selected = entry == tab
                BrutalistButton(
                    label = entry.label,
                    background = if (selected) StudioPalette.Primary else StudioPalette.White,
                    onClick = { tab = entry },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (tab) {
                HubTab.N8N -> RuntimeTab(
                    client = client,
                    status = n8n,
                    endpoint = n8n.endpoint,
                    guiHint = "The in-app GUI is the same local n8n instance the terminal talks to.",
                    onInstall = { payloadPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    installMessage = installMessage,
                    externalOpen = { n8n.endpoint?.let { N8nWebLauncher.open(context, it) } },
                    onRefresh = { client?.refreshInfo() },
                )
                HubTab.OPENCODE -> RuntimeTab(
                    client = client,
                    status = openCode,
                    endpoint = openCode.endpoint,
                    guiHint = "OpenCode serves its own GUI on loopback when the payload provides one; " +
                        "otherwise use the terminal tab, which runs the same server.",
                    onInstall = { payloadPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    installMessage = installMessage,
                    externalOpen = { openCode.endpoint?.let { N8nWebLauncher.open(context, it) } },
                    onRefresh = { client?.refreshInfo() },
                )
                HubTab.TERMINAL -> TerminalPanel(modifier = Modifier.fillMaxWidth())
                HubTab.LOGS -> LogsTab(
                    lines = logs,
                    onClear = {
                        client?.clearLogs(tab.component)
                    },
                )
            }
        }
    }
}

@Composable
private fun RuntimeTab(
    client: RuntimeClient?,
    status: EmbeddedComponentStatus,
    endpoint: String?,
    guiHint: String,
    onInstall: () -> Unit,
    installMessage: String?,
    externalOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val component = status.component

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RuntimeStatus(status)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalistButton(
                label = "START",
                onClick = { scope.launch { client?.start(component) } },
                modifier = Modifier.weight(1f),
            )
            BrutalistButton(
                label = "STOP",
                background = StudioPalette.White,
                onClick = { scope.launch { client?.stop(component) } },
                modifier = Modifier.weight(1f),
            )
            BrutalistButton(
                label = "RESTART",
                background = StudioPalette.White,
                onClick = { scope.launch { client?.restart(component) } },
                modifier = Modifier.weight(1f),
            )
        }

        val running = status.state == EmbeddedProcessState.RUNNING
        BrutalistButton(
            label = if (running) "OPEN LOCAL GUI (in-app)" else "GUI UNAVAILABLE — RUNTIME NOT SERVING",
            background = if (running) StudioPalette.Accent else StudioPalette.White,
            onClick = {
                if (running) {
                    client?.touch(component)
                } else {
                    scope.launch { client?.start(component) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        RuntimeWebPanel(
            url = if (running) endpoint else null,
            height = 320.dp,
            message = if (running) {
                "Waiting for ${component.label} to answer on $endpoint…"
            } else {
                "${component.label} is ${status.state.label().lowercase()}. ${status.message}"
            },
        )
        Text(guiHint, style = MaterialTheme.typography.labelSmall, color = StudioPalette.Muted)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalistButton(
                label = "EXTERNAL BROWSER",
                background = StudioPalette.White,
                onClick = externalOpen,
                modifier = Modifier.weight(1f),
            )
            BrutalistButton(
                label = "REFRESH INFO",
                background = StudioPalette.White,
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
            )
        }

        BrutalistButton(
            label = if (status.payloadReady) "REINSTALL PAYLOAD FROM FILE" else "INSTALL PAYLOAD FROM FILE",
            background = StudioPalette.White,
            onClick = onInstall,
            modifier = Modifier.fillMaxWidth(),
        )
        installMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun LogsTab(lines: List<com.n8n.mobile.studio.runtime.RuntimeLogLine>, onClear: () -> Unit) {
    val listState = rememberLazyListState()
    var filter by remember { mutableStateOf<EmbeddedComponent?>(null) }
    val filtered = remember(lines, filter) {
        (if (filter == null) lines else lines.filter { it.component == filter }).takeLast(400)
    }
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.scrollToItem(filtered.lastIndex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BrutalistButton(
                label = "ALL",
                background = if (filter == null) StudioPalette.Primary else StudioPalette.White,
                onClick = { filter = null },
                modifier = Modifier.weight(1f),
            )
            EmbeddedComponent.entries.forEach { component ->
                BrutalistButton(
                    label = component.label,
                    background = if (filter == component) StudioPalette.Primary else StudioPalette.White,
                    onClick = { filter = component },
                    modifier = Modifier.weight(1f),
                )
            }
            BrutalistButton(
                label = "CLEAR",
                background = StudioPalette.White,
                onClick = onClear,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .border(2.dp, StudioPalette.Primary)
                .background(StudioPalette.White)
                .height(420.dp)
                .padding(10.dp),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    "no runtime output yet — start a runtime to stream its logs here",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = StudioPalette.Muted,
                )
            } else {
                LazyColumn(state = listState) {
                    items(filtered) { line ->
                        Text(
                            "${line.component?.id ?: "studio"} │ ${line.text}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (line.channel ==
                                com.n8n.mobile.studio.runtime.RuntimeLogLine.Channel.SYSTEM
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            color = when (line.channel) {
                                com.n8n.mobile.studio.runtime.RuntimeLogLine.Channel.STDERR ->
                                    androidx.compose.ui.graphics.Color(0xFFC62828)
                                com.n8n.mobile.studio.runtime.RuntimeLogLine.Channel.SYSTEM -> StudioPalette.Muted
                                else -> StudioPalette.Primary
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun summarise(status: EmbeddedComponentStatus): String =
    "${status.component.label} ${status.state.label().lowercase()}"
