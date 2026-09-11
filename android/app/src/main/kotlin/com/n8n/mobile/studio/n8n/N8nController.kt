package com.n8n.mobile.studio.n8n

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.runtime.n8n.N8nRuntime

class N8nController(
    private val runtime: N8nRuntime = N8nRuntime(),
    private val api: N8nApi = N8nApi(),
) {
    fun status(): EmbeddedComponentStatus = runtime.status()

    suspend fun start(context: Context): AppResult<EmbeddedComponentStatus> =
        AppResult.runSuspend { runtime.start(context).getOrThrow() }

    suspend fun stop(): AppResult<Unit> = AppResult.runSuspend { runtime.stop().getOrThrow() }

    suspend fun prepare(context: Context): AppResult<Unit> = AppResult.runSuspend {
        if (status().state != EmbeddedProcessState.RUNNING) {
            start(context).toResult().getOrThrow()
        }
        Unit
    }

    fun openInBrowser(context: Context) = status().endpoint?.let { N8nWebLauncher.open(context, it) }

    suspend fun listWorkflows(baseUrl: String, apiKey: String): AppResult<String> =
        AppResult.runSuspend { api.listWorkflows(baseUrl, apiKey) }
}