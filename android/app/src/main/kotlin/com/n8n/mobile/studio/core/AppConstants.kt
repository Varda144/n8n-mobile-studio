package com.n8n.mobile.studio.core

/**
 * App-wide constants for the app-owned local runtime.
 *
 * Everything the runtime needs lives under the app's private files directory
 * (`/data/data/<applicationId>/files/local-runtime`), so the studio never
 * depends on Termux, a system shell, or an external server.
 */
object AppConstants {
    const val APP_NAME = "N8N Mobile Studio"

    /** Root directory (relative to `filesDir`) owned by the local runtime. */
    const val RUNTIME_DIR = "local-runtime"

    /** n8n listens on loopback only; the same device's browser can reach it. */
    const val LOOPBACK_HOST = "127.0.0.1"

    const val N8N_PORT = 5678
    const val N8N_HEALTH_PATH = "/healthz"

    const val OPENCODE_PORT = 8765
    const val OPENCODE_HEALTH_PATH = "/global/health"

    const val NOTIFICATION_CHANNEL_ID = "local-runtime"
    const val NOTIFICATION_CHANNEL_NAME = "Local runtime"
    const val NOTIFICATION_ID = 2401

    const val ACTION_START = "com.n8n.mobile.studio.runtime.START"
    const val ACTION_STOP = "com.n8n.mobile.studio.runtime.STOP"
    const val ACTION_RESTART = "com.n8n.mobile.studio.runtime.RESTART"
    const val ACTION_START_N8N = "com.n8n.mobile.studio.runtime.START_N8N"
    const val ACTION_STOP_N8N = "com.n8n.mobile.studio.runtime.STOP_N8N"
    const val ACTION_RESTART_N8N = "com.n8n.mobile.studio.runtime.RESTART_N8N"
    const val ACTION_START_OPENCODE = "com.n8n.mobile.studio.runtime.START_OPENCODE"
    const val ACTION_STOP_OPENCODE = "com.n8n.mobile.studio.runtime.STOP_OPENCODE"
    const val ACTION_RESTART_OPENCODE = "com.n8n.mobile.studio.runtime.RESTART_OPENCODE"

    /** Extra carrying an [com.n8n.mobile.studio.runtime.EmbeddedComponent] id. */
    const val EXTRA_COMPONENT = "com.n8n.mobile.studio.runtime.extra.COMPONENT"

    /** Loopback-only cleartext traffic is whitelisted in network_security_config. */
    const val LOCAL_HTTP_SCHEME = "http"

    /** Env var read by `libnoderun.so` to publish the real OS pid of a runtime. */
    const val ENV_PIDFILE = "N8N_STUDIO_PIDFILE"
}
