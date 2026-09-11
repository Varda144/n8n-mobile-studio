package com.n8n.mobile.studio.core

/**
 * Configuration of the app-owned local runtime.
 *
 * Ports are configurable (including being disabled) because a phone may already
 * have something bound on the default port. The runtime never falls back to a
 * remote host: [n8nEndpoint] and [openCodeEndpoint] are always loopback.
 */
data class AppConfig(
    val runtimesDir: String = AppConstants.RUNTIME_DIR,
    val n8nPort: Int = AppConstants.N8N_PORT,
    val openCodePort: Int = AppConstants.OPENCODE_PORT,
    val n8nEnabled: Boolean = true,
    val openCodeEnabled: Boolean = true,
    /** Stop n8n automatically after this much idle time (0 = never). */
    val n8nIdleStopMillis: Long = DEFAULT_IDLE_STOP_MILLIS,
    /** Stop OpenCode automatically after this much idle time (0 = never). */
    val openCodeIdleStopMillis: Long = DEFAULT_IDLE_STOP_MILLIS,
    /** Start n8n as soon as the app's foreground service is created. */
    val autoStartN8n: Boolean = true,
    /** OpenCode is heavier; it is only auto-started when the device allows it. */
    val autoStartOpenCode: Boolean = false,
    /** Never run both runtimes at once on devices below this memory class. */
    val serialStartMemoryClass: Int = 192,
) {
    val n8nEndpoint: String get() = endpoint(n8nPort)
    val openCodeEndpoint: String get() = endpoint(openCodePort)
    val n8nHealthUrl: String get() = n8nEndpoint + AppConstants.N8N_HEALTH_PATH
    val openCodeHealthUrl: String get() = openCodeEndpoint + AppConstants.OPENCODE_HEALTH_PATH

    fun portFor(component: com.n8n.mobile.studio.runtime.EmbeddedComponent): Int = when (component) {
        com.n8n.mobile.studio.runtime.EmbeddedComponent.N8N -> n8nPort
        com.n8n.mobile.studio.runtime.EmbeddedComponent.OPENCODE -> openCodePort
    }

    fun enabled(component: com.n8n.mobile.studio.runtime.EmbeddedComponent): Boolean = when (component) {
        com.n8n.mobile.studio.runtime.EmbeddedComponent.N8N -> n8nEnabled
        com.n8n.mobile.studio.runtime.EmbeddedComponent.OPENCODE -> openCodeEnabled
    }

    fun idleStopMillis(component: com.n8n.mobile.studio.runtime.EmbeddedComponent): Long = when (component) {
        com.n8n.mobile.studio.runtime.EmbeddedComponent.N8N -> n8nIdleStopMillis
        com.n8n.mobile.studio.runtime.EmbeddedComponent.OPENCODE -> openCodeIdleStopMillis
    }

    private fun endpoint(port: Int): String = "${AppConstants.LOCAL_HTTP_SCHEME}://${AppConstants.LOOPBACK_HOST}:$port"

    companion object {
        /** 15 minutes: long enough for an editor session, short enough to free RAM. */
        const val DEFAULT_IDLE_STOP_MILLIS: Long = 15 * 60 * 1000L
    }
}
