package com.n8n.mobile.studio.runtime.service

import android.app.Service
import com.n8n.mobile.studio.runtime.LocalRuntimeManager
import kotlinx.coroutines.flow.StateFlow
import com.n8n.mobile.studio.runtime.RuntimeState

class RuntimeServiceController(private val service:Service){
    private val mgr = LocalRuntimeManager(service)
    val n8nState: StateFlow<RuntimeState> = mgr.n8n.state
    val openCodeState: StateFlow<RuntimeState> = mgr.openCode.state
    suspend fun startN8n()=mgr.startN8n()
    suspend fun stopN8n()=mgr.stopN8n()
    suspend fun restartN8n()=mgr.restartN8n()
    suspend fun startOpenCode()=mgr.startOpenCode()
    suspend fun stopOpenCode()=mgr.stopOpenCode()
    fun n8nUrl()=mgr.n8n.getUrl()
    fun openCodeUrl()=mgr.openCode.getUrl()
    fun isN8nRunning()=mgr.n8n.isRunning()
    fun isOpenCodeRunning()=mgr.openCode.isRunning()
}
