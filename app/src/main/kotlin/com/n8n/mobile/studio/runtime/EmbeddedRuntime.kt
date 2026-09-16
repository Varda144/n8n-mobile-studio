package com.n8n.mobile.studio.runtime
import kotlinx.coroutines.flow.StateFlow
interface EmbeddedRuntime{
    val state: StateFlow<RuntimeState>
    val health: StateFlow<RuntimeHealth>
    val port:Int
    suspend fun start():com.n8n.mobile.studio.core.AppResult<Unit>
    suspend fun stop():com.n8n.mobile.studio.core.AppResult<Unit>
    suspend fun restart():com.n8n.mobile.studio.core.AppResult<Unit>
    fun getUrl():String
    fun isRunning():Boolean
}
