package com.n8n.mobile.studio.local.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** OpenCode lifecycle adapter backed by the APK-owned runtime filesystem. */
class OpenCodeEmbeddedRuntime : EmbeddedRuntime {
    override val component = EmbeddedComponent.OPENCODE

    private var current = EmbeddedComponentStatus(
        component = component,
        message = "Stopped",
    )

    override suspend fun prepare(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        ensureRuntimeDirectories(context).map {
            current = current.copy(message = "OpenCode runtime directories ready")
        }
    }

    override suspend fun start(context: Context): Result<EmbeddedComponentStatus> = withContext(Dispatchers.IO) {
        runCatching {
            current = current.copy(
                state = EmbeddedProcessState.STARTING,
                message = "Preparing embedded OpenCode runtime",
            )
            current = current.copy(
                state = EmbeddedProcessState.RUNNING,
                message = "OpenCode runtime requested",
            )
            current
        }.onFailure {
            current = current.copy(
                state = EmbeddedProcessState.FAILED,
                message = it.message ?: "OpenCode runtime failed",
            )
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            current = current.copy(
                state = EmbeddedProcessState.STOPPED,
                pid = null,
                message = "OpenCode stopped",
            )
        }
    }

    override fun status(): EmbeddedComponentStatus = current
}
