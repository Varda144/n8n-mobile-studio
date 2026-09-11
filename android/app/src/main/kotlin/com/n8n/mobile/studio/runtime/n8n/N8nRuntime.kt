package com.n8n.mobile.studio.runtime.n8n

import android.content.Context
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.runtime.EmbeddedRuntime
import com.n8n.mobile.studio.runtime.RuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class N8nRuntime(
    private val config: N8nConfig = N8nConfig(),
    private val storage: N8nStorage? = null,
    private val process: N8nProcess = N8nProcess(config),
    private val health: N8nHealth = N8nHealth(),
) : EmbeddedRuntime {

    override val component = EmbeddedComponent.N8N

    private val state = RuntimeState(component).apply {
        update { it.copy(endpoint = config.endpoint) }
    }

    private var resolvedStorage: N8nStorage? = null

    private fun storage(context: Context): N8nStorage =
        resolvedStorage ?: (storage ?: N8nStorage(context, config)).also { resolvedStorage = it }

    override suspend fun prepare(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            storage(context).ensure().getOrThrow()
            state.update { it.copy(message = "n8n storage ready") }
        }
    }

    override suspend fun start(context: Context): Result<EmbeddedComponentStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                state.update {
                    it.copy(
                        state = EmbeddedProcessState.STARTING,
                        message = "Preparing embedded n8n runtime",
                    )
                }

                storage(context).ensure().getOrThrow()

                val pid = process.launch(context).getOrThrow()
                state.update {
                    it.copy(
                        state = EmbeddedProcessState.RUNNING,
                        pid = pid,
                        message = "n8n runtime running",
                    )
                }

                runCatching {
                    val status = health.check(config)
                    if (status.reachable) {
                        state.update { it.copy(message = "n8n runtime healthy") }
                    }
                }

                state.get()
            }.onFailure { thrown ->
                state.update {
                    it.copy(
                        state = EmbeddedProcessState.FAILED,
                        message = thrown.message ?: "n8n runtime failed",
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
                    message = "n8n stopped",
                )
            }
        }
    }

    override fun status(): EmbeddedComponentStatus = state.get()
}