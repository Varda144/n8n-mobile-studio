package com.n8n.mobile.studio.runtime.opencode

import android.content.Context
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.runtime.EmbeddedRuntime
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimeState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenCodeRuntime(
    private val config: OpenCodeConfig = OpenCodeConfig(),
    private val process: OpenCodeProcess = OpenCodeProcess(config),
    private val health: OpenCodeHealth = OpenCodeHealth(),
) : EmbeddedRuntime {

    override val component = EmbeddedComponent.OPENCODE

    private val state = RuntimeState(component).apply {
        update { it.copy(endpoint = config.endpoint) }
    }

    private fun root(context: Context): File = File(RuntimePaths.data(context), config.rootFolder)

    override suspend fun prepare(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = root(context)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Failed to create ${dir.absolutePath}")
            }
            state.update { it.copy(message = "OpenCode storage ready") }
        }
    }

    override suspend fun start(context: Context): Result<EmbeddedComponentStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                state.update {
                    it.copy(
                        state = EmbeddedProcessState.STARTING,
                        message = "Preparing embedded OpenCode runtime",
                    )
                }

                val dir = root(context)
                if (!dir.exists() && !dir.mkdirs()) {
                    throw IllegalStateException("Failed to create ${dir.absolutePath}")
                }

                val pid = process.launch(context).getOrThrow()
                state.update {
                    it.copy(
                        state = EmbeddedProcessState.RUNNING,
                        pid = pid,
                        message = "OpenCode runtime running",
                    )
                }

                runCatching {
                    val status = health.check(config)
                    if (status.reachable) {
                        state.update { it.copy(message = "OpenCode runtime healthy") }
                    }
                }

                state.get()
            }.onFailure { thrown ->
                state.update {
                    it.copy(
                        state = EmbeddedProcessState.FAILED,
                        message = thrown.message ?: "OpenCode runtime failed",
                    )
                }
            }
        }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            state.update {
                it.copy(
                    state = EmbeddedProcessState.STOPPED,
                    pid = null,
                    message = "OpenCode stopped",
                )
            }
        }
    }

    override fun status(): EmbeddedComponentStatus = state.get()
}