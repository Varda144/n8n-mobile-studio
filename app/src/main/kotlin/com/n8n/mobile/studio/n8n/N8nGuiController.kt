package com.n8n.mobile.studio.n8n
import android.content.Context
import com.n8n.mobile.studio.runtime.RuntimeState
import kotlinx.coroutines.flow.StateFlow
class N8nGuiController(ctx:Context){
    private val c = N8nController(ctx)
    val state:StateFlow<RuntimeState> = c.state
    suspend fun start()=c.start()
    fun stop()=c.stop()
    fun isRunning()=c.isRunning()
    fun webUrl()=c.url()
}
