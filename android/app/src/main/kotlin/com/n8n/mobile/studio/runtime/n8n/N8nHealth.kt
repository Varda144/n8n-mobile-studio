package com.n8n.mobile.studio.runtime.n8n

import com.n8n.mobile.studio.runtime.RuntimeHealth
import com.n8n.mobile.studio.runtime.RuntimeStatus

class N8nHealth(private val health: RuntimeHealth = RuntimeHealth()) {
    suspend fun check(config: N8nConfig = N8nConfig()): RuntimeStatus = health.probe(config.endpoint)
}