package com.n8n.mobile.studio.runtime

import java.io.File

/**
 * Expands `{placeholder}` tokens in manifest-declared arguments and environment
 * values.
 *
 * Payload builds describe *what* to run without knowing the device layout, e.g.
 *
 * ```
 * "args": ["serve", "--port", "{port}", "--hostname", "127.0.0.1"]
 * ```
 *
 * The supervisor expands those against the app-owned paths at prepare time, so
 * the same payload works for any package name or storage location.
 */
object RuntimeTemplate {

    const val PORT = "port"
    const val HOST = "host"
    const val RUNTIME_ROOT = "runtimeRoot"
    const val COMPONENT_DATA = "dataDir"
    const val COMPONENT_HOME = "homeDir"
    const val COMPONENT_TMP = "tmpDir"
    const val PROJECTS = "projectsDir"
    const val LOG_FILE = "logFile"
    const val PID_FILE = "pidFile"
    const val VERSION = "version"

    fun bindings(
        component: EmbeddedComponent,
        paths: RuntimePaths,
        port: Int,
        version: String,
    ): Map<String, String> = mapOf(
        PORT to port.toString(),
        HOST to "127.0.0.1",
        RUNTIME_ROOT to paths.root.absolutePath,
        COMPONENT_DATA to paths.componentData(component).absolutePath,
        COMPONENT_HOME to paths.componentHome(component).absolutePath,
        COMPONENT_TMP to paths.componentTmp(component).absolutePath,
        PROJECTS to paths.projects.absolutePath,
        LOG_FILE to paths.logFile(component).absolutePath,
        PID_FILE to paths.pidFile(component).absolutePath,
        VERSION to version,
    )

    fun expand(template: String, bindings: Map<String, String>): String {
        if (!template.contains('{')) return template
        var result = template
        bindings.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return result
    }

    fun expandAll(templates: List<String>, bindings: Map<String, String>): List<String> =
        templates.map { expand(it, bindings) }

    fun expandEnv(env: Map<String, String>, bindings: Map<String, String>): Map<String, String> =
        env.mapValues { (_, value) -> expand(value, bindings) }

    /** Tokens left unresolved in [value], for manifest validation messages. */
    fun unresolved(value: String, bindings: Map<String, String>): List<String> =
        Regex("\\{([A-Za-z0-9_]+)}").findAll(value)
            .map { it.groupValues[1] }
            .filterNot { bindings.containsKey(it) }
            .toList()

    fun unresolvedIn(values: Iterable<String>, bindings: Map<String, String>): List<String> =
        values.flatMap { unresolved(it, bindings) }.distinct()

    /** Convenience for the terminal: `prj://name` style references to projects. */
    fun projectDir(paths: RuntimePaths, name: String): File = File(paths.projects, name)
}
