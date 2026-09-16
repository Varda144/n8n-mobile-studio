package com.n8n.mobile.studio.domain.repositories
import com.n8n.mobile.studio.runtime.RuntimeInstaller
import com.n8n.mobile.studio.runtime.RuntimeState
import kotlinx.coroutines.flow.Flow
interface RuntimeRepository{ fun state(t:RuntimeInstaller.RuntimeType):Flow<RuntimeState>; suspend fun start(t:RuntimeInstaller.RuntimeType):Result<Unit>; suspend fun stop(t:RuntimeInstaller.RuntimeType):Result<Unit>}
