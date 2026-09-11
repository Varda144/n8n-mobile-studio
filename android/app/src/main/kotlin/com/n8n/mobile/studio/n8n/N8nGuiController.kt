package com.n8n.mobile.studio.n8n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.runtime.service.RuntimeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Compose-friendly state for the n8n GUI view.
 *
 * It mirrors the supervisor's snapshot (it never inspects processes itself) and
 * exposes the endpoint the embedded WebView or an external browser should load.
 */
class N8nGuiController(private val client: RuntimeClient?) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller = N8nController(client)

    var status: EmbeddedComponentStatus by mutableStateOf(controller.status())
        private set

    val running: Boolean get() = status.state == EmbeddedProcessState.RUNNING

    val endpoint: String? get() = status.endpoint

    fun refresh() {
        status = controller.status()
    }

    fun start() = scope.launch { controller.start() }

    fun stop() = scope.launch { controller.stop() }

    fun restart() = scope.launch { controller.restart() }
}
