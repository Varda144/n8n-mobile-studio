package com.n8n.mobile.studio.runtime.opencode

import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.runtime.RuntimeHealth
import com.n8n.mobile.studio.runtime.RuntimeStatus

/**
 * Health gate for the local OpenCode server.
 *
 * OpenCode's HTTP server exposes a health endpoint next to its API; `/` is kept
 * as a fallback because a payload built from a different upstream tag may serve
 * the console instead. Reachability is what the supervisor gates on — never the
 * mere existence of a pid.
 */
class OpenCodeHealth(private val health: RuntimeHealth = RuntimeHealth()) {

    suspend fun check(config: OpenCodeConfig = OpenCodeConfig()): RuntimeStatus =
        health.probe(config.endpoint, listOf(AppConstants.OPENCODE_HEALTH_PATH, "/"))

    suspend fun isReady(config: OpenCodeConfig = OpenCodeConfig()): Boolean = check(config).reachable
}
