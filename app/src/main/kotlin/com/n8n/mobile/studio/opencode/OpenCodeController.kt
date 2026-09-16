package com.n8n.mobile.studio.opencode
import android.content.Context
import com.n8n.mobile.studio.runtime.LocalRuntimeManager
import com.n8n.mobile.studio.runtime.RuntimeState
import kotlinx.coroutines.flow.StateFlow
class OpenCodeController(ctx:Context){
    private val mgr = LocalRuntimeManager(ctx)
    val state: StateFlow<RuntimeState> = mgr.openCode.state
    suspend fun start()=mgr.startOpenCode()
    suspend fun stop()=mgr.stopOpenCode()
    fun url()=mgr.openCode.getUrl()
    fun isRunning()=mgr.openCode.isRunning()
}
