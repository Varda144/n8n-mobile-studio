package com.n8n.mobile.studio.core

/**
 * App-wide constants for runtime directories, ports and service intents.
 */
object AppConstants {
    const val APP_NAME = "N8N Mobile Studio"
    const val RUNTIME_DIR = "local-runtime"
    const val N8N_PORT = 5678
    const val OPENCODE_PORT = 8765
    const val NOTIFICATION_CHANNEL_ID = "local-runtime"
    const val NOTIFICATION_ID = 2401
    const val ACTION_START = "com.n8n.mobile.studio.runtime.START"
    const val ACTION_STOP = "com.n8n.mobile.studio.runtime.STOP"
    const val ACTION_START_N8N = "com.n8n.mobile.studio.runtime.START_N8N"
    const val ACTION_STOP_N8N = "com.n8n.mobile.studio.runtime.STOP_N8N"
    const val ACTION_START_OPENCODE = "com.n8n.mobile.studio.runtime.START_OPENCODE"
    const val ACTION_STOP_OPENCODE = "com.n8n.mobile.studio.runtime.STOP_OPENCODE"
}