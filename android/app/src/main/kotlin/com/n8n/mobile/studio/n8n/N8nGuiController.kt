package com.n8n.mobile.studio.n8n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.n8n.mobile.studio.runtime.EmbeddedProcessState.RUNNING

class N8nGuiController(private val controller: N8nController = N8nController()) {
    var running: Boolean by mutableStateOf(false)
        private set
    var statusText: String by mutableStateOf("Stopped")
        private set
    var endpoint: String? by mutableStateOf(null)
        private set

    fun refreshFromRuntime() {
        val s = controller.status()
        running = s.state == RUNNING
        statusText = s.message
        endpoint = s.endpoint
    }

    val rawController: N8nController get() = controller
}