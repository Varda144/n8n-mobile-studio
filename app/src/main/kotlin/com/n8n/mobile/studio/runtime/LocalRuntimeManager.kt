package com.n8n.mobile.studio.runtime

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.runtime.n8n.N8nRuntime
import com.n8n.mobile.studio.runtime.opencode.OpenCodeRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalRuntimeManager(ctx: Context) {
    val n8n = N8nRuntime(ctx)
    val openCode = OpenCodeRuntime(ctx)
    private val lock = RuntimeLock()
    suspend fun startN8n():AppResult<Unit> = lock.withLock { n8n.start() }
    suspend fun stopN8n():AppResult<Unit> = lock.withLock { n8n.stop() }
    suspend fun restartN8n():AppResult<Unit> = lock.withLock { n8n.restart() }
    suspend fun startOpenCode():AppResult<Unit> = lock.withLock { openCode.start() }
    suspend fun stopOpenCode():AppResult<Unit> = lock.withLock { openCode.stop() }
}
