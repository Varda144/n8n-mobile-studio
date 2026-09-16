package com.n8n.mobile.studio.runtime.n8n

import android.content.Context
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class N8nRuntime(private val ctx:Context): EmbeddedRuntime {
    override val port = AppConfig.N8N_PORT
    private val installer = RuntimeInstaller(ctx)
    private val proc = N8nProcess(ctx)
    private val _state = MutableStateFlow(RuntimeState.IDLE)
    override val state:StateFlow<RuntimeState> = _state.asStateFlow()
    private val _health = MutableStateFlow(RuntimeHealth.unknown())
    override val health:StateFlow<RuntimeHealth> = _health.asStateFlow()
    override suspend fun start(): AppResult<Unit> {
        if(_state.value==RuntimeState.RUNNING) return AppResult.Success(Unit)
        _state.value = RuntimeState.INSTALLING
        val ins = installer.installN8n()
        if(ins.isFailure){ _state.value=RuntimeState.ERROR; _health.value=RuntimeHealth.error(ins.exceptionOrNull()?.message?:"install failed"); return AppResult.Error(ins.exceptionOrNull()!!) }
        _state.value=RuntimeState.STARTING
        val res = proc.start()
        return if(res.isSuccess){ _state.value=RuntimeState.RUNNING; _health.value=RuntimeHealth.running(proc.pid?:0); Logger.d("n8n RUNNING"); AppResult.Success(Unit)} else { _state.value=RuntimeState.ERROR; _health.value=RuntimeHealth.error(res.exceptionOrNull()?.message?:"start failed"); AppResult.Error(res.exceptionOrNull()!!)}
    }
    override suspend fun stop():AppResult<Unit>{ _state.value=RuntimeState.STOPPING; proc.stop(); _state.value=RuntimeState.STOPPED; _health.value=RuntimeHealth.unknown(); return AppResult.Success(Unit)}
    override suspend fun restart():AppResult<Unit>{ stop(); return start() }
    override fun getUrl()="http://127.0.0.1:$port"
    override fun isRunning()= _state.value==RuntimeState.RUNNING && proc.isRunning
}
