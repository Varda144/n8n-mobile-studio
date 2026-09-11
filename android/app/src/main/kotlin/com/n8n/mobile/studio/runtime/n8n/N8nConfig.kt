package com.n8n.mobile.studio.runtime.n8n

import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimePins
import java.util.TimeZone

/**
 * n8n configuration for a loopback-only phone runtime.
 *
 * The environment below is deliberately explicit and minimal. Every value is
 * chosen so the editor works from the device's own browser or the in-app WebView
 * without a tunnel, without telemetry, and without writing outside the app
 * sandbox. Values are pinned to the packaged n8n version and re-checked by
 * `scripts/verify-runtime.sh` when the payload is built (n8n renames settings
 * between majors).
 *
 * Pure data + pure mapping: unit tested, so an accidental key change is caught.
 */
data class N8nConfig(
    val port: Int = AppConstants.N8N_PORT,
    val host: String = AppConstants.LOOPBACK_HOST,
    val memoryMb: Int = RuntimePins.DEFAULT_NODE_HEAP_MB,
    /** n8n encryption key; generated on device and kept in the Keystore-backed store. */
    val encryptionKey: String = "",
    val timezone: String = TimeZone.getDefault().id,
    val maxExecutionAgeHours: Int = 168,
    val payloadSizeMaxMb: Int = 16,
    val telemetry: Boolean = false,
    val templates: Boolean = false,
) {
    val endpoint: String get() = "${AppConstants.LOCAL_HTTP_SCHEME}://$host:$port"
    val healthUrl: String get() = endpoint + AppConstants.N8N_HEALTH_PATH

    /**
     * Environment for the n8n process. `N8N_SECURE_COOKIE=false` is required:
     * n8n otherwise marks its session cookie `Secure`, which a browser speaking
     * plain HTTP to 127.0.0.1 will refuse to store, breaking login on device.
     */
    fun toEnvironment(paths: RuntimePaths): Map<String, String> = buildMap {
        put("N8N_PORT", port.toString())
        put("N8N_HOST", host)
        put("N8N_LISTEN_ADDRESS", host)
        put("N8N_PROTOCOL", "http")
        put("N8N_SECURE_COOKIE", "false")
        put("N8N_USER_FOLDER", paths.componentData(com.n8n.mobile.studio.runtime.EmbeddedComponent.N8N).absolutePath)
        put("N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS", "false")
        put("N8N_DIAGNOSTICS_ENABLED", telemetry.toString())
        put("N8N_VERSION_NOTIFICATIONS_ENABLED", "false")
        put("N8N_TEMPLATES_ENABLED", templates.toString())
        put("N8N_PERSONALIZATION_ENABLED", "false")
        put("N8N_HIRING_BANNER_ENABLED", "false")
        put("N8N_HIDE_USAGE_PAGE", "true")
        put("EXECUTIONS_DATA_PRUNE", "true")
        put("EXECUTIONS_DATA_MAX_AGE", maxExecutionAgeHours.toString())
        put("N8N_DEFAULT_BINARY_DATA_MODE", "filesystem")
        put("N8N_PAYLOAD_SIZE_MAX", payloadSizeMaxMb.toString())
        put("GENERIC_TIMEZONE", timezone)
        if (encryptionKey.isNotBlank()) put("N8N_ENCRYPTION_KEY", encryptionKey)
    }

    /** Arguments appended to the entry script. */
    fun commandArgs(): List<String> = emptyList()

    companion object {
        /** Environment keys this app sets; used by tests and by the verifier. */
        val MANAGED_KEYS: Set<String> = setOf(
            "N8N_PORT",
            "N8N_HOST",
            "N8N_LISTEN_ADDRESS",
            "N8N_PROTOCOL",
            "N8N_SECURE_COOKIE",
            "N8N_USER_FOLDER",
            "N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS",
            "N8N_DIAGNOSTICS_ENABLED",
            "N8N_VERSION_NOTIFICATIONS_ENABLED",
            "N8N_TEMPLATES_ENABLED",
            "N8N_PERSONALIZATION_ENABLED",
            "N8N_HIRING_BANNER_ENABLED",
            "N8N_HIDE_USAGE_PAGE",
            "EXECUTIONS_DATA_PRUNE",
            "EXECUTIONS_DATA_MAX_AGE",
            "N8N_DEFAULT_BINARY_DATA_MODE",
            "N8N_PAYLOAD_SIZE_MAX",
            "GENERIC_TIMEZONE",
            "N8N_ENCRYPTION_KEY",
        )
    }
}
