package com.n8n.mobile.studio.n8n

import android.content.Context
import com.n8n.mobile.studio.runtime.LocalRuntimeManager
import com.n8n.mobile.studio.runtime.RuntimeState
import kotlinx.coroutines.flow.StateFlow

class N8nController(ctx:Context){
    private val mgr = LocalRuntimeManager(ctx)
    val state:StateFlow<RuntimeState> = mgr.n8n.state
    suspend fun start()=mgr.startN8n()
    suspend fun stop()=mgr.stopN8n()
    suspend fun restart()=mgr.restartN8n()
    fun isRunning()=mgr.n8n.isRunning()
    fun url()=mgr.n8n.getUrl()
}
