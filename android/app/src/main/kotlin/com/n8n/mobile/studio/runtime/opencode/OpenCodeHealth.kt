package com.n8n.mobile.studio.runtime.opencode

import com.n8n.mobile.studio.runtime.RuntimeHealth
import com.n8n.mobile.studio.runtime.RuntimeStatus

class OpenCodeHealth(private val health: RuntimeHealth = RuntimeHealth()) {
    suspend fun check(config: OpenCodeConfig = OpenCodeConfig()): RuntimeStatus =
        health.probe(config.endpoint)
}