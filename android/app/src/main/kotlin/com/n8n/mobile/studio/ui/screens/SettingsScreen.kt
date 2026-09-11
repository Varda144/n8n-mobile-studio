package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.data.preferences.RuntimePreferences
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.service.RuntimeInfo
import com.n8n.mobile.studio.ui.LocalRuntimeClient
import com.n8n.mobile.studio.ui.components.BrutalistButton
import com.n8n.mobile.studio.ui.components.BrutalistCard
import com.n8n.mobile.studio.ui.theme.StudioPalette
import kotlinx.coroutines.launch

/**
 * Runtime settings and on-device diagnostics.
 *
 * Everything here is verifiable from the device itself: which payload version is
 * installed, whether the embedded Node actually starts, what the app owns on
 * disk, and how much memory the device gives it. That is what makes "no fake
 * embedded runtime" checkable without a desktop toolchain.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val client = LocalRuntimeClient.current
    val scope = rememberCoroutineScope()
    val preferences = remember { RuntimePreferences(context) }
    var info by remember { mutableStateOf<RuntimeInfo?>(null) }
    var nodeProbe by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        client?.refreshInfo()
        info = client?.runtimeInfo?.value
    }

    val currentConfig = remember { mutableStateOf(AppConfig()) }
    LaunchedEffect(Unit) {
        preferences.config.collect { currentConfig.value = it }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        BrutalistCard(title = "LOCAL RUNTIME") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleRow(
                    label = "Start n8n with the app",
                    checked = currentConfig.value.autoStartN8n,
                    onChange = { enabled ->
                        val cfg = currentConfig.value
                        scope.launch {
                            preferences.setAutoStart(enabled, cfg.autoStartOpenCode)
                        }
                    },
                )
                ToggleRow(
                    label = "Start OpenCode with the app (heavier)",
                    checked = currentConfig.value.autoStartOpenCode,
                    onChange = { enabled ->
                        val cfg = currentConfig.value
                        scope.launch { preferences.setAutoStart(cfg.autoStartN8n, enabled) }
                    },
                )
                ToggleRow(
                    label = "Embed n8n runtime",
                    checked = currentConfig.value.n8nEnabled,
                    onChange = { enabled ->
                        val cfg = currentConfig.value
                        scope.launch { preferences.setEnabled(enabled, cfg.openCodeEnabled) }
                    },
                )
                ToggleRow(
                    label = "Embed OpenCode runtime",
                    checked = currentConfig.value.openCodeEnabled,
                    onChange = { enabled ->
                        val cfg = currentConfig.value
                        scope.launch { preferences.setEnabled(cfg.n8nEnabled, enabled) }
                    },
                )
                Text(
                    "Idle auto-stop: n8n after ${currentConfig.value.n8nIdleStopMillis / 60_000} min, " +
                        "OpenCode after ${currentConfig.value.openCodeIdleStopMillis / 60_000} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioPalette.Muted,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5L, 15L, 30L, 0L).forEach { minutes ->
                        BrutalistButton(
                            label = if (minutes == 0L) "NEVER" else "${minutes}m",
                            background = StudioPalette.White,
                            onClick = {
                                scope.launch {
                                    preferences.setIdleStop(minutes * 60_000, minutes * 60_000)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5678 to 8765, 7777 to 7778, 8123 to 8124).forEach { (n8nPort, codePort) ->
                        BrutalistButton(
                            label = "$n8nPort/$codePort",
                            background = StudioPalette.White,
                            onClick = { scope.launch { preferences.setPorts(n8nPort, codePort) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "Ports are loopback-only. Changing a port while a runtime is running restarts it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioPalette.Muted,
                )
            }
        }

        BrutalistCard(title = "PAYLOADS") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val infoValue = info
                EmbeddedComponent.entries.forEach { component ->
                    val version = infoValue?.payloadVersions?.get(component)
                    val packaged = infoValue?.packagedComponents?.get(component.id) == true
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${component.label}: ${version ?: "no payload installed"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "packaged in this APK: ${if (packaged) "yes" else "no"} · endpoint http://127.0.0.1:" +
                                (if (component == EmbeddedComponent.N8N) {
                                    currentConfig.value.n8nPort
                                } else {
                                    currentConfig.value.openCodePort
                                }),
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioPalette.Muted,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BrutalistButton(
                                label = "INSTALL BUNDLED",
                                background = StudioPalette.White,
                                onClick = {
                                    scope.launch {
                                        statusMessage = "Installing ${component.label} payload from APK…"
                                        val result = client?.installFromAssets(component)
                                        statusMessage = result?.fold(
                                            onSuccess = { "${component.label} ${it.version} installed" },
                                            onFailure = { "Failed: ${it.message}" },
                                        ) ?: "runtime service not connected"
                                        client?.refreshInfo()
                                        info = client?.runtimeInfo?.value
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            BrutalistButton(
                                label = "UNINSTALL",
                                background = StudioPalette.White,
                                onClick = {
                                    scope.launch {
                                        client?.uninstall(component)
                                        client?.refreshInfo()
                                        info = client?.runtimeInfo?.value
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                BrutalistButton(
                    label = "PROBE EMBEDDED NODE (--version)",
                    background = StudioPalette.Accent,
                    onClick = {
                        scope.launch {
                            nodeProbe = client?.probeNode()?.fold(
                                onSuccess = { "embedded node v$it is runnable on this device" },
                                onFailure = { "embedded node not runnable: ${it.message}" },
                            ) ?: "runtime service not connected"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                nodeProbe?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }

        BrutalistCard(title = "DEVICE / STORAGE") {
            val infoValue = info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (infoValue == null) {
                    Text("runtime service not connected", style = MaterialTheme.typography.bodySmall)
                } else {
                    InfoLine("app", "${infoValue.appVersion} (${infoValue.appVersionCode})")
                    InfoLine("package", infoValue.packageName)
                    InfoLine("abi", infoValue.activeAbi ?: infoValue.abis.joinToString(", "))
                    InfoLine(
                        "node",
                        "packaged=${infoValue.nodePackaged} version=${infoValue.nodeVersion.ifBlank { "?" }} " +
                            "launcher=${infoValue.nodeLauncherPresent} lib=${infoValue.nodeLibraryPresent}",
                    )
                    InfoLine("runtime root", infoValue.runtimeRoot)
                    InfoLine("projects", infoValue.projectsDir)
                    InfoLine("native libs", infoValue.nativeLibraryDir)
                    InfoLine(
                        "memory",
                        "class=${infoValue.memoryClassMb}MiB free=${infoValue.availableMemoryMb}MiB " +
                            "total=${infoValue.totalMemoryMb}MiB",
                    )
                    InfoLine(
                        "credentials",
                        "n8n key=${infoValue.n8nEncryptionKeyPresent} " +
                            "providers=${infoValue.providerCredentials.size}",
                    )
                }
            }
        }

        BrutalistCard(title = "SECURITY") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Provider keys and the n8n encryption key are stored with AES-GCM keys held in the " +
                        "Android Keystore. They are only handed to the local runtime processes through " +
                        "their environment and are never written to logs or the payload.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Runtime endpoints bind to 127.0.0.1 only; nothing is exposed to the network.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        BrutalistCard(title = "DIAGNOSTICS") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    Logger.recent(12).joinToString("\n").ifBlank { "no app log entries yet" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = StudioPalette.Muted,
                )
                BrutalistButton(
                    label = "STOP ALL RUNTIMES",
                    background = StudioPalette.White,
                    onClick = { scope.launch { client?.stopAll() } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioPalette.Muted)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
