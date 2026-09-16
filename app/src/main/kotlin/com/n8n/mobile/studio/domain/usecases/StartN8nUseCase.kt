package com.n8n.mobile.studio.domain.usecases
import com.n8n.mobile.studio.domain.repositories.RuntimeRepository
import com.n8n.mobile.studio.runtime.RuntimeInstaller
class StartN8nUseCase(private val r:RuntimeRepository){ suspend operator fun invoke()=r.start(RuntimeInstaller.RuntimeType.N8N) }
