package com.n8n.mobile.studio.runtime.opencode

import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.InstalledPayload
import com.n8n.mobile.studio.runtime.LaunchSpec
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimeTemplate

/**
 * Builds the OpenCode server [LaunchSpec].
 *
 * OpenCode is launched exactly like n8n — the embedded Node running a bundled
 * script — so both runtimes share one process manager, one log pipeline and one
 * lifecycle policy. The GUI (WebView on the local endpoint, when the payload
 * serves one) and the terminal attach to the same process.
 */
class OpenCodeProcess(
    private val node: NodeRuntime,
    private val paths: RuntimePaths,
    private val config: OpenCodeConfig = OpenCodeConfig(),
) {

    fun spec(
        installed: InstalledPayload,
        entryRelative: String,
        args: List<String>,
        heapMb: Int,
        extraEnv: Map<String, String> = emptyMap(),
    ): LaunchSpec {
        val component = EmbeddedComponent.OPENCODE
        val bindings = RuntimeTemplate.bindings(component, paths, config.port, installed.version)
        val entry = paths.resolveInside(installed.dir, entryRelative)
        val workingDir = paths.componentData(component).apply { mkdirs() }
        config.configDir(paths).mkdirs()
        config.dataDir(paths).mkdirs()
        config.cacheDir(paths).mkdirs()
        config.stateDir(paths).mkdirs()
        val env = config.toEnvironment(paths) +
            mapOf("HOME" to paths.componentHome(component).absolutePath) +
            RuntimeTemplate.expandEnv(extraEnv, bindings)
        return node.launchSpec(
            component = component,
            entry = entry,
            args = RuntimeTemplate.expandAll(args, bindings),
            workingDir = workingDir,
            heapMb = heapMb,
            extraEnv = env,
            logFile = paths.logFile(component),
            pidFile = paths.pidFile(component),
        )
    }
}
