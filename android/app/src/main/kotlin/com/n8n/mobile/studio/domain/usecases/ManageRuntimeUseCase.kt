package com.n8n.mobile.studio.domain.usecases

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.n8n.N8nController
import com.n8n.mobile.studio.opencode.OpenCodeController
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus

class ManageRuntimeUseCase(
    private val n8n: N8nController = N8nController(),
    private val openCode: OpenCodeController = OpenCodeController(),
) {
    suspend fun startN8n(context: Context): AppResult<EmbeddedComponentStatus> = n8n.start(context)

    suspend fun stopN8n(): AppResult<Unit> = n8n.stop()

    fun n8nStatus(): EmbeddedComponentStatus = n8n.status()

    suspend fun startOpenCode(context: Context): AppResult<EmbeddedComponentStatus> = openCode.start(context)

    suspend fun stopOpenCode(): AppResult<Unit> = openCode.stop()
}