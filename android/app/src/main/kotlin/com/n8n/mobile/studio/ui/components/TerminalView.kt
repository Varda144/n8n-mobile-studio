package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.n8n.mobile.studio.runtime.service.RuntimeClient
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.ui.LocalRuntimeClient
import com.n8n.mobile.studio.ui.theme.StudioPalette
import kotlinx.coroutines.launch

/** Read-only rendering of terminal output. */
@Composable
fun TerminalView(
    lines: List<TerminalOutput.Line>,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    BrutalistSurface(modifier = modifier.fillMaxWidth(), shadow = false) {
        Box(Modifier.background(StudioPalette.White).fillMaxWidth().height(height).padding(10.dp)) {
            if (lines.isEmpty()) {
                Text(
                    "--- terminal ready ---\ntype 'help' for the command set",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    color = StudioPalette.Muted,
                )
            } else {
                LazyColumn(state = listState) {
                    items(lines) { line ->
                        Text(
                            line.text,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when (line.channel) {
                                TerminalOutput.Channel.STDERR -> Color(0xFFC62828)
                                TerminalOutput.Channel.STDOUT -> StudioPalette.Primary
                                TerminalOutput.Channel.SYSTEM -> StudioPalette.Muted
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The terminal front-end.
 *
 * Commands are executed by the runtime service, which means `n8n start` here and
 * the "START" button in the GUI hit the same supervisor — a single local runtime,
 * two interfaces, exactly as required.
 */
@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    sessionId: String = "studio-terminal",
    height: Dp = 280.dp,
) {
    val client = LocalRuntimeClient.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf(listOf<TerminalOutput.Line>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TerminalView(lines, height = height)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("ls", fontFamily = FontFamily.Monospace) },
                label = { Text("command") },
            )
            BrutalistButton(
                label = if (busy) "…" else "RUN",
                onClick = {
                    val command = input.trim()
                    if (command.isEmpty() || busy || client == null) return@BrutalistButton
                    input = ""
                    busy = true
                    scope.launch {
                        val result = client.runCommand(sessionId, command) { event ->
                            if (event is TerminalOutput.Line) lines = lines + event
                        }
                        result.onFailure { error ->
                            lines = lines + TerminalOutput.Line(
                                "error: ${error.message}",
                                TerminalOutput.Channel.SYSTEM,
                            )
                        }
                        busy = false
                    }
                    client.touch(com.n8n.mobile.studio.runtime.EmbeddedComponent.N8N)
                },
                modifier = Modifier.height(56.dp),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalistButton(
                label = "CLEAR",
                background = StudioPalette.White,
                onClick = { lines = emptyList() },
                modifier = Modifier.weight(1f),
            )
            BrutalistButton(
                label = "PATHS",
                background = StudioPalette.White,
                onClick = {
                    lines = lines + TerminalOutput.Line("~ = app-owned runtime directory", TerminalOutput.Channel.SYSTEM)
                    lines = lines + TerminalOutput.Line(
                        "projects = ${context.filesDir.absolutePath}/local-runtime/projects",
                        TerminalOutput.Channel.SYSTEM,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
