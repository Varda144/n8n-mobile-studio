package com.n8n.mobile.studio.runtime.n8n

import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.InstalledPayload
import com.n8n.mobile.studio.runtime.LaunchSpec
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimeTemplate

/**
 * Turns an installed n8n payload plus its manifest entry into a concrete
 * [LaunchSpec]. The process is Node with n8n's CLI script as the first argument —
 * the same command a desktop user would run, executed by the app's embedded Node.
 */
class N8nProcess(
    private val node: NodeRuntime,
    private val paths: RuntimePaths,
    private val config: N8nConfig = N8nConfig(),
) {

    fun spec(
        installed: InstalledPayload,
        entryRelative: String,
        args: List<String>,
        heapMb: Int,
        extraEnv: Map<String, String> = emptyMap(),
    ): LaunchSpec {
        val component = EmbeddedComponent.N8N
        val bindings = RuntimeTemplate.bindings(component, paths, config.port, installed.version)
        val entry = paths.resolveInside(installed.dir, entryRelative)
        val workingDir = paths.componentData(component).apply { mkdirs() }
        val env = config.toEnvironment(paths) + RuntimeTemplate.expandEnv(extraEnv, bindings)
        return node.launchSpec(
            component = component,
            entry = entry,
            args = RuntimeTemplate.expandAll(args, bindings) + config.commandArgs(),
            workingDir = workingDir,
            heapMb = heapMb,
            extraEnv = env,
            logFile = paths.logFile(component),
            pidFile = paths.pidFile(component),
        )
    }
}
