package com.n8n.mobile.studio.domain.usecases

import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.n8n.N8nController
import com.n8n.mobile.studio.opencode.OpenCodeController
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.service.RuntimeClient

/**
 * Domain-level entry point for runtime lifecycle actions.
 *
 * Everything goes through the singleton [RuntimeClient] (the foreground
 * service), so use cases and screens cannot start processes of their own — the
 * app has exactly one process manager.
 */
class ManageRuntimeUseCase(private val client: RuntimeClient?) {

    private val n8n = N8nController(client)
    private val openCode = OpenCodeController(client)

    suspend fun startN8n(): AppResult<Unit> = n8n.start()

    suspend fun stopN8n(): AppResult<Unit> = n8n.stop()

    suspend fun restartN8n(): AppResult<Unit> = n8n.restart()

    fun n8nStatus(): EmbeddedComponentStatus = n8n.status()

    suspend fun startOpenCode(): AppResult<Unit> = openCode.start()

    suspend fun stopOpenCode(): AppResult<Unit> = openCode.stop()

    suspend fun restartOpenCode(): AppResult<Unit> = openCode.restart()

    fun openCodeStatus(): EmbeddedComponentStatus = openCode.status()

    suspend fun startAll(): AppResult<Unit> = AppResult.runSuspend {
        client?.startAll() ?: error("runtime service is not connected")
    }

    suspend fun stopAll(): AppResult<Unit> = AppResult.runSuspend {
        client?.stopAll() ?: error("runtime service is not connected")
    }

    fun allStatuses(): List<EmbeddedComponentStatus> =
        client?.statuses?.value?.values?.toList().orEmpty()
}
