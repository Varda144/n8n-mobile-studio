package com.n8n.mobile.studio.opencode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.service.RuntimeClient
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.terminal.TerminalOutputBuffer

/**
 * State for OpenCode's two surfaces.
 *
 * [guiUrl] is only non-null when the local server is actually answering, so the
 * GUI can never render an empty frame that looks like a working agent. Terminal
 * output is buffered with the same bounded buffer the terminal screen uses.
 */
class OpenCodeGuiState(private val client: RuntimeClient? = null) {

    var status: EmbeddedComponentStatus by mutableStateOf(
        client?.statuses?.value?.get(com.n8n.mobile.studio.runtime.EmbeddedComponent.OPENCODE)
            ?: EmbeddedComponentStatus(com.n8n.mobile.studio.runtime.EmbeddedComponent.OPENCODE),
    )
        private set

    var projectsDir: String by mutableStateOf("")
        private set

    val buffer = TerminalOutputBuffer()

    val running: Boolean
        get() = status.state == com.n8n.mobile.studio.runtime.EmbeddedProcessState.RUNNING

    val guiUrl: String? get() = status.endpoint?.takeIf { running }

    val statusText: String get() = status.message

    fun refresh() {
        status = client?.statuses?.value?.get(com.n8n.mobile.studio.runtime.EmbeddedComponent.OPENCODE)
            ?: status
    }

    /**
     * The property keeps a private setter, so the value is only ever changed
     * through this method; naming it `setProjectsDir` would collide with the
     * property's own JVM setter.
     */
    fun updateProjectsDir(path: String) {
        projectsDir = path
    }

    fun onEvent(event: TerminalOutput) {
        if (event is TerminalOutput.Line) buffer.add(event)
    }

    fun clear() = buffer.clear()
}
