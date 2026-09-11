package com.n8n.mobile.studio.local.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * n8n runtime adapter. The executable/package is deliberately resolved from the
 * APK-owned runtime directory instead of Termux or a remote server.
 *
 * The actual n8n distribution is supplied by the build/runtime packaging phase;
 * this class owns lifecycle and health semantics once that distribution exists.
 */
class N8nEmbeddedRuntime : EmbeddedRuntime {
    override val component = EmbeddedComponent.N8N

    private var current = EmbeddedComponentStatus(
        component = component,
        endpoint = ENDPOINT,
    )

    override suspend fun prepare(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        ensureRuntimeDirectories(context).map {
            current = current.copy(message = "Runtime directories ready")
        }
    }

    override suspend fun start(context: Context): Result<EmbeddedComponentStatus> = withContext(Dispatchers.IO) {
        runCatching {
            current = current.copy(
                state = EmbeddedProcessState.STARTING,
                message = "Preparing embedded n8n runtime",
            )

            // Process launching is intentionally centralized in LocalRuntimeService.
            // Keeping this adapter process-agnostic lets the bundled n8n distribution
            // change without changing the Compose UI.
            current = current.copy(
                state = EmbeddedProcessState.RUNNING,
                message = "n8n runtime requested",
            )
            current
        }.onFailure {
            current = current.copy(
                state = EmbeddedProcessState.FAILED,
                message = it.message ?: "n8n runtime failed",
            )
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            current = current.copy(
                state = EmbeddedProcessState.STOPPED,
                pid = null,
                message = "n8n stopped",
            )
        }
    }

    override fun status(): EmbeddedComponentStatus = current

    companion object {
        const val PORT = 5678
        const val ENDPOINT = "http://127.0.0.1:5678"
    }
}
