package com.n8n.mobile.studio.runtime.opencode

import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimePins
import java.io.File

/**
 * OpenCode configuration for a sandboxed phone runtime.
 *
 * OpenCode keeps its configuration, state and caches under XDG directories. On
 * Android those are pointed inside the app sandbox so the agent's project scope
 * is exactly `local-runtime/projects` and nothing else. Provider keys are only
 * added when the user has actually stored them (Keystore-backed
 * [com.n8n.mobile.studio.security.SecureStorage]) — the runtime never receives a
 * credential the user did not enter.
 */
data class OpenCodeConfig(
    val port: Int = AppConstants.OPENCODE_PORT,
    val host: String = AppConstants.LOOPBACK_HOST,
    val memoryMb: Int = RuntimePins.DEFAULT_NODE_HEAP_MB,
    /** Only the providers the user configured, already filtered. */
    val providerKeys: Map<String, String> = emptyMap(),
    /** Projects root exposed to the agent. */
    val projectsDir: String = "projects",
    val disableAutoUpdate: Boolean = true,
) {
    val endpoint: String get() = "${AppConstants.LOCAL_HTTP_SCHEME}://$host:$port"
    val healthUrl: String get() = endpoint + AppConstants.OPENCODE_HEALTH_PATH

    fun configDir(paths: RuntimePaths): File =
        File(paths.componentData(EmbeddedComponent.OPENCODE), "config")

    fun dataDir(paths: RuntimePaths): File =
        File(paths.componentData(EmbeddedComponent.OPENCODE), "share")

    fun cacheDir(paths: RuntimePaths): File =
        File(paths.componentTmp(EmbeddedComponent.OPENCODE), "cache")

    fun stateDir(paths: RuntimePaths): File =
        File(paths.componentData(EmbeddedComponent.OPENCODE), "state")

    fun toEnvironment(paths: RuntimePaths): Map<String, String> = buildMap {
        put("XDG_CONFIG_HOME", configDir(paths).absolutePath)
        put("XDG_DATA_HOME", dataDir(paths).absolutePath)
        put("XDG_CACHE_HOME", cacheDir(paths).absolutePath)
        put("XDG_STATE_HOME", stateDir(paths).absolutePath)
        put("OPENCODE_CONFIG", File(configDir(paths), "opencode/opencode.json").absolutePath)
        put("OPENCODE_DISABLE_AUTOUPDATE", disableAutoUpdate.toString())
        put("OPENCODE_PORT", port.toString())
        put("OPENCODE_HOSTNAME", host)
        // The agent's filesystem scope: the app-owned projects directory.
        put("OPENCODE_WORKSPACE", File(paths.projects, projectsDir).absolutePath)
        putAll(providerKeys)
    }

    fun commandArgs(): List<String> = listOf("--hostname", host, "--port", port.toString())

    companion object {
        /**
         * Environment keys this app manages. Provider secrets are intentionally
         * absent: they are added dynamically from secure storage.
         */
        val MANAGED_KEYS: Set<String> = setOf(
            "XDG_CONFIG_HOME",
            "XDG_DATA_HOME",
            "XDG_CACHE_HOME",
            "XDG_STATE_HOME",
            "OPENCODE_CONFIG",
            "OPENCODE_DISABLE_AUTOUPDATE",
            "OPENCODE_PORT",
            "OPENCODE_HOSTNAME",
            "OPENCODE_WORKSPACE",
        )

        /** Provider credential env names the UI may collect. */
        val PROVIDER_KEYS: List<String> = listOf(
            "ANTHROPIC_API_KEY",
            "OPENAI_API_KEY",
            "OPENROUTER_API_KEY",
            "GOOGLE_GENERATIVE_AI_API_KEY",
            "GROQ_API_KEY",
            "MISTRAL_API_KEY",
        )
    }
}
