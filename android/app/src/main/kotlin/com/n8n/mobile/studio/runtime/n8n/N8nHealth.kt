package com.n8n.mobile.studio.runtime.n8n

import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.runtime.RuntimeHealth
import com.n8n.mobile.studio.runtime.RuntimeStatus

/**
 * Health gate for the local n8n instance.
 *
 * `/healthz` is n8n's own liveness endpoint; `/` is the fallback because the
 * editor answering with HTML (or a redirect to the setup wizard) is equally good
 * evidence that the local process is serving.
 */
class N8nHealth(private val health: RuntimeHealth = RuntimeHealth()) {

    suspend fun check(config: N8nConfig = N8nConfig()): RuntimeStatus =
        health.probe(config.endpoint, listOf(AppConstants.N8N_HEALTH_PATH, "/"))

    suspend fun isReady(config: N8nConfig = N8nConfig()): Boolean = check(config).reachable
}
