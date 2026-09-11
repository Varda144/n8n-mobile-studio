package com.n8n.mobile.studio.n8n

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.service.RuntimeClient

/**
 * Facade over the local n8n runtime for app features that are not the runtime hub.
 *
 * It forwards to the same [RuntimeClient] the GUI and the terminal use, so there
 * is exactly one n8n process on the device, started and stopped exactly once.
 */
class N8nController(private val client: RuntimeClient?) {

    fun status(): EmbeddedComponentStatus =
        client?.statuses?.value?.get(EmbeddedComponent.N8N)
            ?: EmbeddedComponentStatus(EmbeddedComponent.N8N)

    suspend fun start(): AppResult<Unit> = AppResult.runSuspend {
        requireNotNull(client) { "runtime service is not connected" }
        client.start(EmbeddedComponent.N8N)
    }

    suspend fun stop(): AppResult<Unit> = AppResult.runSuspend {
        requireNotNull(client) { "runtime service is not connected" }
        client.stop(EmbeddedComponent.N8N)
    }

    suspend fun restart(): AppResult<Unit> = AppResult.runSuspend {
        requireNotNull(client) { "runtime service is not connected" }
        client.restart(EmbeddedComponent.N8N)
    }

    /** Start n8n if it is not serving yet, then hand back its loopback endpoint. */
    suspend fun ensureRunning(): AppResult<String> = AppResult.runSuspend {
        requireNotNull(client) { "runtime service is not connected" }
        if (status().state != com.n8n.mobile.studio.runtime.EmbeddedProcessState.RUNNING) {
            client.start(EmbeddedComponent.N8N)
        }
        status().endpoint ?: error("n8n endpoint is not configured")
    }

    fun openInBrowser(context: Context) {
        status().endpoint?.let { N8nWebLauncher.open(context, it) }
    }

    fun touch() = client?.touch(EmbeddedComponent.N8N)
}
