package com.n8n.mobile.studio.opencode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.terminal.TerminalOutputBuffer

/** Compose-observable state backing the OpenCode screen. */
class OpenCodeGuiState {
    var running: Boolean by mutableStateOf(false)
        private set

    var statusText: String by mutableStateOf("Stopped")
        private set

    var session: OpenCodeSession? by mutableStateOf(null)
        private set

    val output: List<TerminalOutput.Line> get() = buffer.lines

    private val buffer = TerminalOutputBuffer()

    fun onEvent(output: TerminalOutput) {
        when (output) {
            is TerminalOutput.Line -> buffer.add(output)
            is TerminalOutput.Exited -> {
                running = false
                statusText = "Exited (${output.exitCode})"
            }
        }
    }

    fun markStarting() {
        running = true
        statusText = "Starting"
    }

    fun markRunning(pid: Long) {
        running = true
        statusText = "Running"
    }

    fun markStopped() {
        running = false
        statusText = "Stopped"
        session = null
    }

    fun updateSession(session: OpenCodeSession) {
        this.session = session
        running = session.running
    }

    fun clear() = buffer.clear()
}