package com.n8n.mobile.studio.domain.usecases
import com.n8n.mobile.studio.domain.repositories.RuntimeRepository
import com.n8n.mobile.studio.runtime.RuntimeInstaller
class StopN8nUseCase(private val r:RuntimeRepository){ suspend operator fun invoke()=r.stop(RuntimeInstaller.RuntimeType.N8N) }
